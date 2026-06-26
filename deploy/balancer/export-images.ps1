<#
.SYNOPSIS
  Сохраняет Docker-образы проекта в один .tar для переноса на другой ноут.
  Запускать на СИЛЬНОМ ноуте, где образы уже собраны.

.PARAMETER Project
  Имя docker-compose проекта, под которым собраны образы (по умолчанию game-camp,
  как в balance.ps1). Образы compose называет "<project>-<service>".

.PARAMETER OutFile
  Куда сохранить архив.

.EXAMPLE
  .\deploy\balancer\export-images.ps1 -OutFile D:\camp-images.tar
#>
param(
  [string]$Project = "game-camp",
  [string]$OutFile = "camp-images.tar",
  [string[]]$BaseImages = @("postgres:16", "apache/kafka:3.8.0", "4km3/dnsmasq:latest")
)
$ErrorActionPreference = "Stop"

# Собранные образы проекта (например game-camp-banking-service).
$built = docker images "$Project-*" --format "{{.Repository}}:{{.Tag}}" |
  Where-Object { $_ -and ($_ -notmatch "<none>") }

if (-not $built) {
  Write-Warning "Образы '$Project-*' не найдены. Проверь имя проекта: docker images"
  Write-Warning "Если собирал не через balance.ps1, укажи своё -Project."
}

# Оставляем только реально присутствующие базовые образы (kafka-ui опционален).
$present = docker images --format "{{.Repository}}:{{.Tag}}"
foreach ($b in $BaseImages) {
  if ($present -notcontains $b) { Write-Warning "Базовый образ не найден локально, пропускаю: $b (скачай: docker pull $b)" }
}
$basePresent = $BaseImages | Where-Object { $present -contains $_ }

$all = @($basePresent + $built) | Select-Object -Unique
Write-Host "Сохраняю образы в $OutFile :" -ForegroundColor Cyan
$all | ForEach-Object { Write-Host "  $_" }

docker save -o $OutFile @all
if ($LASTEXITCODE -ne 0) { throw "docker save завершился с кодом $LASTEXITCODE" }

$sizeGB = [math]::Round((Get-Item $OutFile).Length / 1GB, 2)
Write-Host ("Готово: {0} ({1} ГБ). Скопируй файл на второй ноут и запусти import-images.ps1" -f $OutFile, $sizeGB) -ForegroundColor Green
