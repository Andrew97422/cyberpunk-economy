<#
.SYNOPSIS
  Загружает Docker-образы из .tar. Запускать на ВТОРОМ ноуте (где только
  Docker Desktop), после копирования архива с сильного ноута.

.EXAMPLE
  .\deploy\balancer\import-images.ps1 -InFile D:\camp-images.tar
#>
param(
  [string]$InFile = "camp-images.tar"
)
$ErrorActionPreference = "Stop"

if (-not (Test-Path $InFile)) { throw "Файл не найден: $InFile" }

Write-Host "Загружаю образы из $InFile ..." -ForegroundColor Cyan
docker load -i $InFile
if ($LASTEXITCODE -ne 0) { throw "docker load завершился с кодом $LASTEXITCODE" }

Write-Host "`nГотово. Образы на этой машине:" -ForegroundColor Green
docker images
