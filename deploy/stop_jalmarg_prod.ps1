# ==================================================================================
# JALMARG PRODUCTION SHUTDOWN SCRIPT (PowerShell)
# TARGET: Safely terminates JalMarg Spring Boot process on port 8080
# ==================================================================================

Write-Host "Stopping JalMarg backend on port 8080..." -ForegroundColor Cyan

$connections = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if ($connections) {
    foreach ($conn in $connections) {
        $procId = $conn.OwningProcess
        if ($procId -gt 0) {
            Write-Host "Stopping process PID $procId listening on port 8080..." -ForegroundColor Yellow
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Host "JalMarg backend stopped." -ForegroundColor Green
} else {
    Write-Host "No process listening on port 8080." -ForegroundColor Green
}
