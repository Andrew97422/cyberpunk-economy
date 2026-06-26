<#
.SYNOPSIS
  Резервная копия всех баз данных из запущенных контейнеров postgres.
  Работает в любой конфигурации (all-in-one или сплит) — сам находит все БД.

.DESCRIPTION
  Делает pg_dump каждой базы (с --clean --if-exists, чтобы восстановление было
  идемпотентным), складывает в папку с меткой времени, по желанию копирует на
  второй ноут и подчищает старые копии.

.PARAMETER OutDir
  Куда складывать копии (по умолчанию D:\camp-backups).

.PARAMETER KeepLast
  Сколько последних копий хранить (старые удаляются).

.PARAMETER PeerShare
  UNC-путь к общей папке на втором ноуте, напр. \\192.168.1.11\Backups —
  туда продублируется копия. Пусто = не копировать.

.EXAMPLE
  .\deploy\balancer\backup.ps1 -PeerShare \\192.168.1.11\Backups
#>
param(
  [string]$OutDir = "D:\camp-backups",
  [int]$KeepLast = 48,
  [string]$PeerShare = ""
)
$ErrorActionPreference = "Continue"  # docker пишет warning в stderr; не валим скрипт

$pg = docker ps --format "{{.Names}}" | Where-Object { $_ -match "postgres" }
if (-not $pg) { Write-Warning "Нет запущенных контейнеров postgres — нечего сохранять."; return }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dest = Join-Path $OutDir $stamp
New-Item -ItemType Directory -Force -Path $dest | Out-Null

foreach ($c in $pg) {
  $raw = docker exec $c psql -U postgres -tAc "SELECT datname FROM pg_database WHERE datistemplate=false AND datname<>'postgres';"
  $dbs = $raw -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }
  foreach ($db in $dbs) {
    # дамп пишем в файл ВНУТРИ контейнера и копируем наружу — без искажений кодировки
    docker exec $c sh -c "pg_dump -U postgres --clean --if-exists -d $db > /tmp/$db.sql"
    docker cp "${c}:/tmp/$db.sql" (Join-Path $dest "$db.sql")
    docker exec $c rm -f "/tmp/$db.sql"
  }
  Write-Host ("  {0}: сохранено БД — {1}" -f $c, $dbs.Count) -ForegroundColor Green
}
Write-Host ("Бэкап готов: {0}" -f $dest) -ForegroundColor Cyan

# Копия на второй ноут
if ($PeerShare -ne "") {
  try {
    $peerDest = Join-Path $PeerShare $stamp
    New-Item -ItemType Directory -Force -Path $peerDest | Out-Null
    Copy-Item (Join-Path $dest "*") $peerDest -Force -ErrorAction Stop
    Write-Host ("Копия на второй ноут: {0}" -f $peerDest) -ForegroundColor Cyan
  } catch {
    Write-Warning ("Не удалось скопировать на {0}: {1}" -f $PeerShare, $_.Exception.Message)
  }
}

# Чистка старых копий
$old = Get-ChildItem $OutDir -Directory | Sort-Object Name -Descending | Select-Object -Skip $KeepLast
foreach ($o in $old) { Remove-Item $o.FullName -Recurse -Force }
