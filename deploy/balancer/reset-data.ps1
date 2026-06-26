<#
.SYNOPSIS
  ПОЛНОЕ обнуление данных: удаляет тома (БД + Kafka + медиа) и поднимает стек
  заново с чистого листа. Останется только админ (admin/admin12345) и рантайм-
  дефолты — без товаров, операций, сессий и прочих данных.

  ⚠️ НЕОБРАТИМО. Все данные игры будут стёрты. Используй ПЕРЕД мероприятием/сменой,
  а НЕ во время — иначе потеряешь живые данные.

.PARAMETER Yes
  Подтверждение. Без него скрипт ничего не делает (защита от случайного запуска).

.EXAMPLE
  .\deploy\balancer\reset-data.ps1 -Yes
#>
param(
  [string]$Project = "game-camp",
  [string]$ComposeFile = "docker-compose.yaml",
  [switch]$Yes
)
$ErrorActionPreference = "Continue"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

if (-not $Yes) {
  Write-Host "ВНИМАНИЕ: это сотрёт ВСЕ данные игры (кроме админа, который создастся заново)." -ForegroundColor Red
  Write-Host "Если уверен — запусти с ключом -Yes:" -ForegroundColor Yellow
  Write-Host "  .\deploy\balancer\reset-data.ps1 -Yes" -ForegroundColor Yellow
  return
}

Push-Location $repoRoot
try {
  Write-Host "=== Удаляю данные (down -v) ===" -ForegroundColor Red
  & docker compose -p $Project -f $ComposeFile down -v
} finally {
  Pop-Location
}

Write-Host "`n=== Поднимаю чистый стек ===" -ForegroundColor Cyan
& (Join-Path $PSScriptRoot "start-all.ps1") -Project $Project -ComposeFile $ComposeFile

Write-Host "`nГотово. Данные обнулены, остался только админ (admin/admin12345)." -ForegroundColor Green
