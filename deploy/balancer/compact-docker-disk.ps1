<#
.SYNOPSIS
  Сжимает виртуальный диск Docker (docker_data.vhdx), возвращая Windows место,
  которое освободилось внутри Docker (удалённые образы/кэш), но осталось занятым
  в раздутом vhdx.

  ⚠️ ЗАПУСКАТЬ ОТ ИМЕНИ АДМИНИСТРАТОРА (diskpart этого требует).
     Правый клик по файлу → «Запустить с помощью PowerShell» не даст прав;
     открой PowerShell «от имени администратора» и запусти оттуда:
       powershell -ExecutionPolicy Bypass -File "D:\Лагерь июнь 2026\deploy\balancer\compact-docker-disk.ps1"

  Что делает: гасит стек и Docker, останавливает WSL, сжимает диск, поднимает всё
  обратно. Данные (тома) НЕ теряются — compact убирает только пустые блоки.
#>
param(
  [string]$Vhdx = "$env:LOCALAPPDATA\Docker\wsl\disk\docker_data.vhdx"
)
$ErrorActionPreference = "Continue"

# --- Проверка прав администратора ---
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
  Write-Host "НЕТ прав администратора. Открой PowerShell 'от имени администратора' и запусти этот скрипт оттуда." -ForegroundColor Red
  return
}
if (-not (Test-Path $Vhdx)) {
  Write-Host "Не найден файл диска: $Vhdx" -ForegroundColor Red
  Write-Host "Проверь путь: dir `$env:LOCALAPPDATA\Docker\wsl -Recurse -Filter *.vhdx" -ForegroundColor Yellow
  return
}

$freeBefore = [math]::Round((Get-PSDrive C).Free / 1GB, 1)
$sizeBefore = [math]::Round((Get-Item $Vhdx).Length / 1GB, 2)
Write-Host ("До: C свободно {0} ГБ; vhdx {1} ГБ" -f $freeBefore, $sizeBefore) -ForegroundColor Cyan

# --- 1. Погасить стек (тома сохраняются) ---
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Push-Location $repoRoot
try { docker compose -p game-camp down } catch {} finally { Pop-Location }

# --- 2. Остановить Docker Desktop и WSL ---
Write-Host "Останавливаю Docker Desktop и WSL..." -ForegroundColor Yellow
Get-Process -Name "Docker Desktop", "com.docker.backend", "com.docker.dev-envs" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 4
wsl --shutdown
Start-Sleep -Seconds 6

# --- 3. Сжать диск через diskpart (attach readonly = безопасно) ---
Write-Host "Сжимаю диск (может занять несколько минут)..." -ForegroundColor Yellow
$script = @"
select vdisk file="$Vhdx"
attach vdisk readonly
compact vdisk
detach vdisk
exit
"@
$tmp = Join-Path $env:TEMP "compact_docker.txt"
Set-Content -Path $tmp -Value $script -Encoding ascii
diskpart /s $tmp
Remove-Item $tmp -ErrorAction SilentlyContinue

$sizeAfter = [math]::Round((Get-Item $Vhdx).Length / 1GB, 2)

# --- 4. Поднять Docker и стек обратно ---
Write-Host "Запускаю Docker Desktop..." -ForegroundColor Yellow
$exe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
if (Test-Path $exe) { Start-Process $exe }
for ($i = 0; $i -lt 48; $i++) { Start-Sleep 5; docker info *> $null; if ($LASTEXITCODE -eq 0) { break } }
& (Join-Path $PSScriptRoot "start-all.ps1")

$freeAfter = [math]::Round((Get-PSDrive C).Free / 1GB, 1)
Write-Host ("`nИтог: vhdx {0} ГБ -> {1} ГБ; C свободно {2} ГБ -> {3} ГБ (вернулось ~{4} ГБ)" -f `
    $sizeBefore, $sizeAfter, $freeBefore, $freeAfter, [math]::Round($freeAfter - $freeBefore, 1)) -ForegroundColor Green
