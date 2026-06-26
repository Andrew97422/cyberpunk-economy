<#
.SYNOPSIS
  Автоматическое распределение сервисов проекта по двум ноутам в зависимости
  от объёма ОЗУ каждой машины.

.DESCRIPTION
  Скрипт сам определяет, сколько памяти на текущем ноуте, берёт объём второго
  ноута из параметра -PeerRamGB и считает оптимальную раскладку: группы сервисов
  раскидываются так, чтобы минимизировать пиковую загрузку памяти любого из двух
  ноутов. Группа CORE (Kafka + шлюз + фронтенд) всегда закреплена за "входной"
  нодой — машиной, помеченной флагом -Entry.

  Оба ноута, запущенные с одинаковыми числами, получат ОДИНАКОВЫЙ план (расчёт
  детерминированный), поэтому никакой связи между ними для планирования не нужно —
  каждый просто стартует свою долю.

.PARAMETER Entry
  Пометить ЭТУ машину как входную (на ней Kafka + шлюз + фронтенд, к ней
  подключаются устройства). Указывается ТОЛЬКО на одном — более сильном — ноуте.

.PARAMETER PeerRamGB
  Объём ОЗУ ВТОРОГО ноута в гигабайтах (целое, напр. 8). Обязателен.

.PARAMETER ThisRamGB
  Переопределить автоопределение ОЗУ текущей машины (для тестов). Необязателен.

.PARAMETER CoreHost
  LAN-IP входной ноды. Если задан — обновит строку CORE_HOST в .env. Необязателен.

.PARAMETER Build
  Пересобрать образы (нужен интернет!). По умолчанию выключено — в закрытой сети
  запускаемся из готовых образов.

.PARAMETER DryRun
  Только показать план, ничего не запускать.

.EXAMPLE
  # На сильном ноуте (16 ГБ), он же входной, второй ноут — 8 ГБ:
  .\balance.ps1 -Entry -PeerRamGB 8 -CoreHost 192.168.1.10

.EXAMPLE
  # На слабом ноуте (8 ГБ), второй — 16 ГБ:
  .\balance.ps1 -PeerRamGB 16
#>
[CmdletBinding()]
param(
  [switch]$Entry,
  [Parameter(Mandatory = $true)][int]$PeerRamGB,
  [double]$ThisRamGB = 0,
  [string]$CoreHost = "",
  [switch]$Build,
  [switch]$DryRun
)

$ErrorActionPreference = "Continue"  # docker пишет warning в stderr; не валим скрипт
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$manifest = Get-Content (Join-Path $PSScriptRoot "services.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$safety = [double]$manifest.safetyFactor
$groups = $manifest.groups

# --- 1. Сколько памяти на этой машине ---------------------------------------
if ($ThisRamGB -le 0) {
  $bytes = (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
  $ThisRamGB = [math]::Round($bytes / 1GB, 1)
}
Write-Host ("Эта машина: {0} ГБ ОЗУ; роль: {1}" -f $ThisRamGB, $(if ($Entry) { "ВХОДНАЯ (core здесь)" } else { "рабочая" })) -ForegroundColor Cyan

# --- 2. Собираем картину обеих нод: индекс 0 = входная, 1 = рабочая ----------
if ($Entry) {
  $entryRamGB = $ThisRamGB; $workerRamGB = $PeerRamGB
} else {
  $entryRamGB = $PeerRamGB; $workerRamGB = $ThisRamGB
}
$budgetMB = @(($entryRamGB * 1024 * $safety), ($workerRamGB * 1024 * $safety))
$nodeLabel = @("ВХОДНАЯ", "рабочая")

$pinned = @($groups | Where-Object { $_.pinToEntry })
$floating = @($groups | Where-Object { -not $_.pinToEntry })

# --- 3. Перебор всех раскладок плавающих групп; ищем минимальный пик ----------
$baseLoad = @(0.0, 0.0)
foreach ($g in $pinned) { $baseLoad[0] += [double]$g.weightMB }   # закреплены за нодой 0

$best = $null
$combos = [int][math]::Pow(2, $floating.Count)
for ($mask = 0; $mask -lt $combos; $mask++) {
  $load = @($baseLoad[0], $baseLoad[1])
  $assign = @{}
  for ($i = 0; $i -lt $floating.Count; $i++) {
    $node = ($mask -shr $i) -band 1
    $load[$node] += [double]$floating[$i].weightMB
    $assign[$floating[$i].name] = $node
  }
  $peak = [math]::Max($load[0] / $budgetMB[0], $load[1] / $budgetMB[1])
  if ($null -eq $best -or $peak -lt $best.peak) {
    $best = @{ peak = $peak; load = $load; assign = $assign }
  }
}

# --- 4. Печатаем план для ОБЕИХ нод ------------------------------------------
Write-Host "`n=== План распределения ===" -ForegroundColor Yellow
for ($n = 0; $n -lt 2; $n++) {
  $ramGB = @($entryRamGB, $workerRamGB)[$n]
  $names = @()
  foreach ($g in $pinned) { if ($n -eq 0) { $names += $g.name } }
  foreach ($g in $floating) { if ($best.assign[$g.name] -eq $n) { $names += $g.name } }
  $util = [math]::Round(100 * $best.load[$n] / $budgetMB[$n], 0)
  $color = if ($util -gt 100) { "Red" } elseif ($util -gt 85) { "Yellow" } else { "Green" }
  Write-Host ("  {0,-8} ({1} ГБ): группы [{2}] -> резерв {3} МБ, загрузка {4}% от безопасного лимита" -f `
      $nodeLabel[$n], $ramGB, ($names -join ", "), [int]$best.load[$n], $util) -ForegroundColor $color
}
if ($best.peak -gt 1.0) {
  Write-Host "`n[!] Памяти впритык — лучший вариант всё равно превышает безопасный лимит." -ForegroundColor Red
  Write-Host "    Контейнеры защищены mem_limit, но возможны OOM-перезапуски. Подумай о третьей машине" -ForegroundColor Red
  Write-Host "    или о дроблении на отдельные сервисы. Свяжись со мной — сделаем мельче." -ForegroundColor Red
}

# --- 5. Что запускаем на ЭТОЙ машине -----------------------------------------
$thisNode = if ($Entry) { 0 } else { 1 }
$myGroups = @()
foreach ($g in $pinned) { if ($thisNode -eq 0) { $myGroups += $g } }
foreach ($g in $floating) { if ($best.assign[$g.name] -eq $thisNode) { $myGroups += $g } }

if ($myGroups.Count -eq 0) {
  Write-Host "`nНа эту машину сервисы не назначены. Нечего запускать." -ForegroundColor Yellow
  return
}
Write-Host ("`nНа ЭТОЙ машине запускаю: {0}" -f (($myGroups | ForEach-Object { $_.name }) -join ", ")) -ForegroundColor Cyan

# --- 6. (опц.) обновляем CORE_HOST в .env ------------------------------------
if ($CoreHost -ne "") {
  $envPath = Join-Path $repoRoot ".env"
  if (Test-Path $envPath) {
    $lines = Get-Content $envPath
    $lines = $lines | ForEach-Object { if ($_ -match '^\s*CORE_HOST\s*=') { "CORE_HOST=$CoreHost" } else { $_ } }
    $encNoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($envPath, $lines, $encNoBom)
    Write-Host ("CORE_HOST в .env установлен в {0}" -f $CoreHost) -ForegroundColor Green
  } else {
    Write-Host "[!] .env не найден — пропускаю обновление CORE_HOST." -ForegroundColor Yellow
  }
}

# --- 7. Собираем и запускаем docker compose ----------------------------------
$composeArgs = @("compose", "-p", "game-camp")
foreach ($g in $myGroups) {
  foreach ($f in $g.compose) { $composeArgs += @("-f", $f) }
}
$composeArgs += @("up", "-d")
if ($Build) { $composeArgs += "--build" }

Write-Host ("`n> docker {0}" -f ($composeArgs -join " ")) -ForegroundColor DarkGray
if ($DryRun) {
  Write-Host "(DryRun — ничего не запущено)" -ForegroundColor Yellow
  return
}

Push-Location $repoRoot
try {
  & docker @composeArgs
  if ($LASTEXITCODE -ne 0) { throw "docker compose завершился с кодом $LASTEXITCODE" }
  Write-Host "`nГотово. Проверь статус: docker compose -p game-camp ps" -ForegroundColor Green
} finally {
  Pop-Location
}
