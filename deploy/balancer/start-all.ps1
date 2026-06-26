<#
.SYNOPSIS
  СУПЕР-СКРИПТ: запускает весь стек одной командой (конфигурация Primary).
  Проверяет/запускает Docker, поднимает all-in-one, ждёт готовности, проверяет.

.PARAMETER Build
  Пересобрать образы (нужен интернет). По умолчанию выкл — запуск из готовых.

.PARAMETER WaitSec
  Сколько ждать готовности БД (по умолчанию 240).

.EXAMPLE
  .\deploy\balancer\start-all.ps1
#>
param(
  [string]$Project = "game-camp",
  [string]$ComposeFile = "docker-compose.yaml",
  [switch]$Build,
  [int]$WaitSec = 240
)
# Continue (не Stop): нативные docker-команды пишут безобидные warning в stderr,
# а при Stop это валит скрипт. Контроль ошибок — по $LASTEXITCODE ниже.
$ErrorActionPreference = "Continue"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

# --- 1. Docker запущен? Если нет — поднять Docker Desktop и подождать ---------
docker info *> $null
if ($LASTEXITCODE -ne 0) {
  Write-Host "Docker не запущен — стартую Docker Desktop..." -ForegroundColor Yellow
  $exe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
  if (-not (Test-Path $exe)) {
    $exe = (Get-ChildItem "C:\Program Files\Docker" -Recurse -Filter "Docker Desktop.exe" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
  }
  if ($exe) { Start-Process $exe }
  for ($i = 0; $i -lt 48; $i++) {
    Start-Sleep -Seconds 5
    docker info *> $null
    if ($LASTEXITCODE -eq 0) { break }
    Write-Host ("  ... жду Docker ({0} сек)" -f (($i + 1) * 5))
  }
  if ($LASTEXITCODE -ne 0) { throw "Docker не поднялся. Запусти Docker Desktop вручную и повтори." }
}
Write-Host "Docker готов." -ForegroundColor Green

# --- 2. Поднять весь стек ----------------------------------------------------
Push-Location $repoRoot
try {
  $composeArgs = @("compose", "-p", $Project, "-f", $ComposeFile, "up", "-d")
  if ($Build) { $composeArgs += "--build" }
  Write-Host ("> docker {0}" -f ($composeArgs -join " ")) -ForegroundColor DarkGray
  & docker @composeArgs
  if ($LASTEXITCODE -ne 0) { throw "docker compose up завершился с кодом $LASTEXITCODE" }

  # --- 3. Дождаться готовности Postgres ---------------------------------------
  Write-Host "Жду готовности базы (postgres healthy)..." -ForegroundColor Cyan
  $deadline = (Get-Date).AddSeconds($WaitSec)
  do {
    Start-Sleep -Seconds 5
    $pgHealth = (docker inspect -f "{{.State.Health.Status}}" game-postgres 2>$null)
  } while ($pgHealth -ne "healthy" -and (Get-Date) -lt $deadline)
  Write-Host ("postgres: {0}" -f $pgHealth)
} finally {
  Pop-Location
}

# --- 4. Статус + сквозная проверка ------------------------------------------
Write-Host "`n=== Контейнеры ===" -ForegroundColor Yellow
docker compose -p $Project ps

Write-Host "`n[i] Сервисам нужно ~30-60 сек на прогрев. Проверка логина:" -ForegroundColor DarkGray
$ok = $false
for ($i = 0; $i -lt 12; $i++) {
  Start-Sleep -Seconds 5
  try {
    $body = '{"publicName":"admin","password":"admin12345"}'
    Invoke-RestMethod -Uri "http://localhost:8080/api/auth/admin/login" -Method Post `
      -ContentType "application/json" -Body $body -TimeoutSec 5 | Out-Null
    $ok = $true; break
  } catch { }
}
if ($ok) { Write-Host "OK: шлюз отвечает, логин работает." -ForegroundColor Green }
else { Write-Host "Шлюз ещё прогревается — подожди минуту и открой сайт." -ForegroundColor Yellow }

Write-Host "`nГотово. Открывай:  http://localhost  (с других устройств — http://bank.lan или http://<IP этого ноута>)" -ForegroundColor Green
Write-Host "Имя bank.lan заработает после настройки DNS на роутере — см. ИНСТРУКЦИЯ/09-КРАСИВЫЙ-АДРЕС-DNS.md" -ForegroundColor DarkGray
Write-Host "Логин админа: admin / admin12345" -ForegroundColor Green
