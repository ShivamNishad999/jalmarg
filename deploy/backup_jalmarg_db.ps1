# ==================================================================================
# JALMARG DATABASE BACKUP SCRIPT (PowerShell)
# TARGET: 127.0.0.1:5433 / jalmarg_twin
# ABSOLUTE SAFETY: Port 5432 (CarSync) is strictly prohibited and isolated!
# ==================================================================================

$ErrorActionPreference = "Stop"

$PG_HOST = "127.0.0.1"
$PG_PORT = "5433"
$PG_DB   = "jalmarg_twin"
$PG_USER = "postgres"
$PG_DUMP = "D:\JalMargPostGIS\bin\pg_dump.exe"

$TIMESTAMP = Get-Date -Format "yyyyMMdd_HHmmss"
$BACKUP_DIR = "D:\jalmarg\jalmarg\backups"
$BACKUP_FILE = "$BACKUP_DIR\jalmarg_twin_backup_$TIMESTAMP.sql"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "JALMARG POSTGIS DATABASE BACKUP UTILITY" -ForegroundColor Cyan
Write-Host "Target Host: $PG_HOST" -ForegroundColor Yellow
Write-Host "Target Port: $PG_PORT (Port 5432 is strictly prohibited)" -ForegroundColor Yellow
Write-Host "Target Database: $PG_DB" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Verification of Target Port
if ($PG_PORT -ne "5433") {
    Write-Error "ABORT: Illegal port configured ($PG_PORT). JalMarg MUST target port 5433 only."
    exit 1
}

if (-not (Test-Path $PG_DUMP)) {
    Write-Error "ABORT: pg_dump executable not found at $PG_DUMP."
    exit 1
}

if (-not (Test-Path $BACKUP_DIR)) {
    New-Item -ItemType Directory -Path $BACKUP_DIR | Out-Null
}

Write-Host "Executing pg_dump for $PG_DB on port $PG_PORT..." -ForegroundColor Green
& $PG_DUMP -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB -F c -b -v -f $BACKUP_FILE

if ($LASTEXITCODE -eq 0 -and (Test-Path $BACKUP_FILE)) {
    $size = (Get-Item $BACKUP_FILE).Length
    Write-Host "SUCCESS: Backup completed successfully!" -ForegroundColor Green
    Write-Host "Destination: $BACKUP_FILE ($size bytes)" -ForegroundColor Green
} else {
    Write-Error "Backup execution failed with exit code $LASTEXITCODE."
    exit 1
}
