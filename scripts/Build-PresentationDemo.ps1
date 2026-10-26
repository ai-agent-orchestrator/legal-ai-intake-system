[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$frontend=Join-Path $projectRoot 'frontend/legal-procedure-viewer'
$destination=Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local/presentation-2026-10-25/dist'
$previous=$env:VITE_API_BASE_URL
Push-Location $frontend
try{
    # Build-only API base. No .env/application.properties or OpenAI credential is changed.
    $env:VITE_API_BASE_URL='http://127.0.0.1:18080'
    & (Get-Command node.exe).Source node_modules/vite/bin/vite.js build --outDir $destination
    if($LASTEXITCODE){throw 'Presentation frontend build failed'}
}finally{$env:VITE_API_BASE_URL=$previous;Pop-Location}
Write-Output 'Presentation build ready:5176 intake ->18080 mock backend; operator09-13 ->8080 fixed server. Main frontend dist unchanged.'
