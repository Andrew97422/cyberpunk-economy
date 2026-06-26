<#
.SYNOPSIS
  Восстановление баз данных из папки бэкапа в запущенные контейнеры postgres.
  Сопоставляет файлы <имя_бд>.sql с нужным контейнером автоматически.

.DESCRIPTION
  Применяй ПОСЛЕ того, как подняты контейнеры postgres, но ДО (или с последующим
  перезапуском) сервисов. Файлы дампа делались с --clean, поэтому повторное
  восстановление перезатирает данные начисто.

.PARAMETER BackupDir
  Папка с файлами *.sql (одна метка времени из D:\camp-backups).

.EXAMPLE
  .\deploy\balancer\restore.ps1 -BackupDir D:\camp-backups\20260626-141500
#>
param(
  [Parameter(Mandatory = $true)][string]$BackupDir
)
$ErrorActionPreference = "Continue"  # docker пишет warning в stderr; не валим скрипт

if (-not (Test-Path $BackupDir)) { throw "Папка не найдена: $BackupDir" }
$files = Get-ChildItem $BackupDir -Filter *.sql
if (-not $files) { throw "В папке нет файлов *.sql: $BackupDir" }

# Карта: имя БД -> контейнер, где она есть
$pg = docker ps --format "{{.Names}}" | Where-Object { $_ -match "postgres" }
if (-not $pg) { throw "Нет запущенных контейнеров postgres. Сначала подними БД." }
$index = @{}
foreach ($c in $pg) {
  $raw = docker exec $c psql -U postgres -tAc "SELECT datname FROM pg_database WHERE datistemplate=false;"
  ($raw -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }) | ForEach-Object {
    if (-not $index.ContainsKey($_)) { $index[$_] = $c }
  }
}

foreach ($f in $files) {
  $db = $f.BaseName
  $c = $index[$db]
  if (-not $c) { Write-Warning ("БД '{0}' нет среди запущенных — пропуск" -f $db); continue }
  docker cp $f.FullName "${c}:/tmp/restore.sql"
  docker exec $c sh -c "psql -U postgres -d $db -f /tmp/restore.sql" | Out-Null
  docker exec $c rm -f "/tmp/restore.sql"
  Write-Host ("  восстановлено: {0} -> {1}" -f $db, $c) -ForegroundColor Green
}
Write-Host "Готово. Если сервисы уже запущены — перезапусти их, чтобы увидели данные." -ForegroundColor Cyan
