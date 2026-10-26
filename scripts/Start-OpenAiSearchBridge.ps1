# Starts (or stops) the OpenAI web search bridge for the P03 lawyer search demo. Paid: ~$0.0112 (~16 KRW) per search.
#   .\scripts\Start-OpenAiSearchBridge.ps1                 # start, cap 20 paid searches (worst case ~$0.23, ~320 KRW)
#   .\scripts\Start-OpenAiSearchBridge.ps1 -MaxSearches 5
#   .\scripts\Start-OpenAiSearchBridge.ps1 -Stop
# Then start the server with: .\scripts\Start-LlmTestServer.ps1 -Mode RealLlm -Port 8080 -SearchBridgePort 18282
# The key goes ONLY into the node child process environment (never an argument, never printed, never on disk in plain text).
[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)][int]$Port = 18282,
    [ValidateRange(1, 50)][int]$MaxSearches = 20,
    [switch]$Stop
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $projectRoot 'backend/guardrail-ready-policy-checker'
$keyFile = Join-Path $backendPath '.local/secrets/openai-key.dpapi'
$runDirectory = Join-Path $backendPath '.local/llm-test'
$stateFile = Join-Path $runDirectory 'search-bridge.json'
$bridge = Join-Path $PSScriptRoot 'openai-search-bridge.mjs'
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

if ($Stop) {
    if (-not (Test-Path -LiteralPath $stateFile)) { Write-Host 'No bridge state. Nothing to stop.'; exit 0 }
    $s = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    try { Write-Host ('Usage: ' + (Invoke-RestMethod "http://127.0.0.1:$($s.port)/__bridge__/stats" -TimeoutSec 3 | ConvertTo-Json -Compress)) } catch {}
    Stop-Process -Id $s.pid -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stateFile
    Write-Host "Stopped bridge pid $($s.pid)." -ForegroundColor Green
    exit 0
}

if (-not (Test-Path -LiteralPath $keyFile)) { throw 'STOP: No saved key. Run scripts\Set-LocalOpenAiKey.ps1 first.' }
if (-not (Test-Path -LiteralPath $bridge)) { throw "STOP: $bridge not found." }
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "STOP: Port $Port is already in use (bridge already running? use -Stop)." }
$worst = $MaxSearches * (0.01 + 8000 * 0.15 / 1e6 + 600 * 0.60 / 1e6 + 600 * 0.15 / 1e6)
Write-Host ''
Write-Host '=== OpenAI web search bridge (PAID) ===' -ForegroundColor Yellow
Write-Host "Listens on      : http://127.0.0.1:$Port (loopback only)"
Write-Host "Paid search cap : $MaxSearches (then the bridge refuses with 429)"
Write-Host 'Per lawyer search: 1 paid web search (the two P03 legs share it), ~$0.0112 (~16 KRW)'
Write-Host ("Worst case      : about `${0:N3} (~{1:N0} KRW at 1,400)" -f $worst, ($worst * 1400))
Write-Host ''
if ((Read-Host 'Type y to start the paid bridge (anything else cancels)') -cne 'y') { Write-Host 'Cancelled.'; exit 0 }

$secureKey = $null; $keyPointer = [IntPtr]::Zero
$saved = @{ k = $env:OPENAI_SEARCH_BRIDGE_KEY; p = $env:BRIDGE_PORT; m = $env:BRIDGE_MAX_SEARCHES; l = $env:BRIDGE_LOG }
try {
    $secureKey = Get-Content -LiteralPath $keyFile -Raw | ConvertTo-SecureString
    $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
    $env:OPENAI_SEARCH_BRIDGE_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    $env:BRIDGE_PORT = "$Port"; $env:BRIDGE_MAX_SEARCHES = "$MaxSearches"
    $env:BRIDGE_LOG = Join-Path $runDirectory 'search-bridge-log.jsonl'
    # A visible window shows one log line per paid search (counts and cost only).
    $proc = Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList "`"$bridge`"" -WorkingDirectory $projectRoot -PassThru
}
finally {
    $env:OPENAI_SEARCH_BRIDGE_KEY = $saved.k; $env:BRIDGE_PORT = $saved.p; $env:BRIDGE_MAX_SEARCHES = $saved.m; $env:BRIDGE_LOG = $saved.l
    if ($keyPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer) }
    if ($secureKey) { $secureKey.Dispose() }
}
[ordered]@{ pid = $proc.Id; port = $Port; maxSearches = $MaxSearches; started = (Get-Date).ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
$deadline = (Get-Date).AddSeconds(15)
while ((Get-Date) -lt $deadline) {
    if ($proc.HasExited) { throw 'STOP: Bridge exited during startup (see its window).' }
    try { $s = Invoke-RestMethod "http://127.0.0.1:$Port/__bridge__/stats" -TimeoutSec 2; Write-Host "READY: bridge pid $($proc.Id), cap $($s.max), used $($s.searches)" -ForegroundColor Green; exit 0 } catch { Start-Sleep -Milliseconds 500 }
}
throw 'STOP: Bridge did not become ready in 15s.'
