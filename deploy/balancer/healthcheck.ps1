<#
.SYNOPSIS
  Быстрая проверка состояния: контейнеры, шлюз+Kafka, доступность второго ноута.
  Запускай, когда что-то «подозрительно», или повесь на повтор.

.EXAMPLE
  .\deploy\balancer\healthcheck.ps1 -EntryIp 192.168.1.10 -PeerIp 192.168.1.11
#>
param(
  [string]$EntryIp = "192.168.1.10",
  [string]$PeerIp = ""
)

Write-Host "=== Контейнеры (этот ноут) ===" -ForegroundColor Yellow
docker compose -p game-camp ps

Write-Host "`n=== Шлюз + Kafka (сквозная проверка) ===" -ForegroundColor Yellow
# Креды админа берём из .env (НЕ хардкодим).
$envFile = Join-Path $PSScriptRoot "..\..\.env"
$admName = "admin"
$admPass = $env:BOOTSTRAP_ADMIN_PASSWORD
if (-not $admPass -and (Test-Path $envFile)) {
  Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*BOOTSTRAP_ADMIN_NAME\s*=\s*(.+?)\s*$')     { $admName = $Matches[1] }
    if ($_ -match '^\s*BOOTSTRAP_ADMIN_PASSWORD\s*=\s*(.+?)\s*$') { $admPass = $Matches[1] }
  }
}
try {
  if (-not $admPass) { throw "BOOTSTRAP_ADMIN_PASSWORD не задан (env или .env)" }
  $body = @{ publicName = $admName; password = $admPass } | ConvertTo-Json -Compress
  $r = Invoke-RestMethod -Uri ("http://{0}:8080/api/auth/admin/login" -f $EntryIp) `
    -Method Post -ContentType "application/json" -Body $body -TimeoutSec 5
  Write-Host "OK: шлюз ответил, токен получен (значит, связка через Kafka жива)." -ForegroundColor Green
} catch {
  Write-Host ("ПРОБЛЕМА: шлюз не отвечает — {0}" -f $_.Exception.Message) -ForegroundColor Red
  Write-Host "Смотри логи: docker compose -p game-camp logs --tail=50 core" -ForegroundColor DarkGray
}

Write-Host "`n=== Локальный DNS (имя bank.lan) ===" -ForegroundColor Yellow
if (docker ps --filter "name=game-dns" --format "{{.Names}}") {
  try {
    $a = Resolve-DnsName -Name bank.lan -Server 127.0.0.1 -Type A -DnsOnly -ErrorAction Stop
    Write-Host ("OK: bank.lan -> {0}" -f ($a.IPAddress -join ", ")) -ForegroundColor Green
  } catch {
    Write-Host "ПРОБЛЕМА: DNS-контейнер запущен, но имя не резолвится." -ForegroundColor Red
  }
} else {
  Write-Host "DNS-контейнер game-dns не запущен (имя bank.lan работать не будет)." -ForegroundColor Yellow
}

if ($PeerIp -ne "") {
  Write-Host "`n=== Второй ноут ===" -ForegroundColor Yellow
  if (Test-Connection $PeerIp -Count 1 -Quiet) {
    Write-Host ("OK: {0} доступен." -f $PeerIp) -ForegroundColor Green
  } else {
    Write-Host ("ПРОБЛЕМА: {0} не пингуется. Если это узел с сервисами — см. failover в 08." -f $PeerIp) -ForegroundColor Red
  }
}
