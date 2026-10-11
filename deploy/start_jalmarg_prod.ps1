# ==================================================================================
# JALMARG PRODUCTION STARTUP SCRIPT (PowerShell)
# TARGET: Spring Boot with --spring.profiles.active=prod
# DATABASE: 127.0.0.1:5433 / jalmarg_twin
# ==================================================================================

$ErrorActionPreference = "Stop"

$JAR_PATH = "D:\jalmarg\jalmarg\backend\target\jalmarg-backend-1.0.0.jar"
$PORT = 8080

Write-Host "Verifying PostGIS runtime on port 5433..." -ForegroundColor Cyan
try {
    $connTest = & "D:\JalMargPostGIS\bin\psql.exe" -U postgres -p 5433 -d jalmarg_twin -t -c "SELECT 1;"
    if ($connTest.Trim() -ne "1") {
        throw "Unexpected query response."
    }
    Write-Host "PostGIS 5433 verified." -ForegroundColor Green
} catch {
    Write-Error "PostGIS database on port 5433 is not reachable! Aborting startup."
    exit 1
}

if (-not (Test-Path $JAR_PATH)) {
    Write-Host "Building backend jar..." -ForegroundColor Yellow
    Push-Location "D:\jalmarg\jalmarg\backend"
    mvn clean package -DskipTests
    Pop-Location
}

Write-Host "Starting JalMarg Backend with prod profile..." -ForegroundColor Green
$process = Start-Process java -ArgumentList "-jar", $JAR_PATH, "--spring.profiles.active=prod" -PassThru -NoNewWindow
Write-Host "Process started with PID: $($process.Id)" -ForegroundColor Green

Write-Host "Waiting for health check on http://localhost:$PORT/api/health..." -ForegroundColor Cyan
Start-Sleep -Seconds 5
try {
    $health = Invoke-RestMethod -Uri "http://localhost:$PORT/api/health" -Method Get -TimeoutSec 10
    Write-Host "JalMarg is UP and operational: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "Health check pending or initializing..." -ForegroundColor Yellow
}
