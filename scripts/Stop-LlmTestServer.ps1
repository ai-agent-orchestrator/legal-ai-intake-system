# Stops only the server started by Start-LlmTestServer.ps1 (pid recorded in .local/llm-test/server.json).
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$stateFile = Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local/llm-test/server.json'
if (-not (Test-Path -LiteralPath $stateFile)) { Write-Host 'No LLM test server recorded. Nothing to stop.'; exit 0 }
$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
$proc = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
if ($proc -and $proc.ProcessName -like 'java*') {
    Stop-Process -Id $state.pid
    Write-Host "Stopped LLM test server pid $($state.pid) (mode $($state.mode), port $($state.port))."
} else {
    Write-Host "Recorded pid $($state.pid) is not running (or is not java). Nothing stopped."
}
Remove-Item -LiteralPath $stateFile
