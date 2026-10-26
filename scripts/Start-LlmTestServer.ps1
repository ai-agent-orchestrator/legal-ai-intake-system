# LLM test server (separate from Start-LocalServer.ps1). Loopback only, in-memory DB, NAVER search -> local mock.
#   -Mode Mock    : guard + LLM both go to the local mock provider (127.0.0.1:18181). Cost 0.
#   -Mode RealLlm : guard -> local mock provider, LLM -> real OpenAI with the DPAPI-saved key. Paid.
# The key is passed ONLY as an environment variable of the java child process (never as an argument,
# never printed, never written to disk in plain text).
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('Mock', 'RealLlm')][string]$Mode,
    [ValidateRange(1024, 65535)][int]$Port = 18090,
    [ValidateRange(1024, 65535)][int]$MockPort = 18181,
    [ValidateRange(16, 512)][int]$MaxOutputTokens = 64,
    # Cost-control experiments may only LOWER the budgets/timeouts below the source defaults, never raise them.
    [ValidateRange(0.000001, 2.0)][double]$MonthlyBudgetUsd = 2.0,
    [ValidateRange(0.000001, 0.5)][double]$DailyBudgetUsd = 0.5,
    [ValidateRange(0.000001, 0.12)][double]$SessionBudgetUsd = 0.12,
    [ValidateRange(1, 30000)][int]$RequestTimeoutMs = 30000,
    # 0 = NAVER mock (default). Port of Start-OpenAiSearchBridge.ps1 = real OpenAI web search for the P03 lawyer search.
    [ValidateRange(0, 65535)][int]$SearchBridgePort = 0
)
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $projectRoot 'backend/guardrail-ready-policy-checker'
$secretDirectory = Join-Path $backendPath '.local/secrets'
$keyFile = Join-Path $secretDirectory 'openai-key.dpapi'
$runDirectory = Join-Path $backendPath '.local/llm-test'
$stateFile = Join-Path $runDirectory 'server.json'
$builtJar = Join-Path $backendPath 'build/libs/ai-agent-security-observability-lab-0.0.1-SNAPSHOT.jar'

function Test-Listening([int]$p) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $p -ErrorAction SilentlyContinue)
}

# ---- Prechecks (nothing is started if any fails) ----
if (-not (Test-Path -LiteralPath $builtJar)) {
    throw "STOP: Backend JAR not found. Build it first: cd backend\guardrail-ready-policy-checker; .\gradlew.bat bootJar"
}
if (Test-Listening $Port) { throw "STOP: Port $Port is already in use. Stop that server first (Stop-LlmTestServer.ps1)." }
if (-not (Test-Listening $MockPort)) {
    throw "STOP: Mock provider is not running on $MockPort. Start it in another window: node frontend\legal-procedure-viewer\scripts\p03-mock-providers.mjs"
}
foreach ($scope in 'User', 'Machine') {
    foreach ($name in 'LLM_API_KEY', 'OPENAI_API_KEY') {
        if ([Environment]::GetEnvironmentVariable($name, $scope)) {
            throw "STOP: $name is set in the Windows $scope environment. Remove it; keys must not live there."
        }
    }
}
if ($SearchBridgePort -gt 0 -and -not (Test-Listening $SearchBridgePort)) {
    throw "STOP: Search bridge is not running on $SearchBridgePort. Start it first: .\scripts\Start-OpenAiSearchBridge.ps1"
}
if ($Mode -eq 'RealLlm' -and -not (Test-Path -LiteralPath $keyFile)) {
    throw 'STOP: No saved key. Run scripts\Set-LocalOpenAiKey.ps1 first.'
}

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stdout = Join-Path $runDirectory "$Mode-$stamp.stdout.log"
$stderr = Join-Path $runDirectory "$Mode-$stamp.stderr.log"

$mockUrl = "http://127.0.0.1:$MockPort"
$llmUrl = if ($Mode -eq 'Mock') { $mockUrl } else { 'https://api.openai.com' }
$searchUrl = if ($SearchBridgePort -gt 0) { "http://127.0.0.1:$SearchBridgePort" } else { $mockUrl }
$searchMax = if ($SearchBridgePort -gt 0) { 3 } else { 1 }
$searchTimeout = if ($SearchBridgePort -gt 0) { 45000 } else { 10000 }
$javaArgs = @(
    '-jar', $builtJar,
    '--server.address=127.0.0.1', "--server.port=$Port",
    '--spring.jpa.show-sql=false', '--spring.h2.console.enabled=false',
    "--guardrails.base-url=$mockUrl",
    "--llm.base-url=$llmUrl",
    '--llm.model=gpt-4o-mini',
    "--llm.max-output-tokens=$MaxOutputTokens",
    "--llm.request-timeout-ms=$RequestTimeoutMs",
    ('--llm.cost.monthly-budget-usd=' + $MonthlyBudgetUsd.ToString([Globalization.CultureInfo]::InvariantCulture)),
    ('--llm.cost.daily-budget-usd=' + $DailyBudgetUsd.ToString([Globalization.CultureInfo]::InvariantCulture)),
    ('--llm.cost.session-budget-usd=' + $SessionBudgetUsd.ToString([Globalization.CultureInfo]::InvariantCulture)),
    # P03 search: the NAVER-shaped endpoint is the local mock, or the OpenAI web search bridge (dummy NAVER credentials either way).
    '--search.enabled=true', "--search.naver.base-url=$searchUrl",
    '--search.naver.client-id=mock-client', '--search.naver.client-secret=mock-secret',
    "--search.max-results=$searchMax", "--search.request-timeout-ms=$searchTimeout"
)

$previous = @{ LLM_API_KEY = $env:LLM_API_KEY; OPENAI_API_KEY = $env:OPENAI_API_KEY }
$secureKey = $null; $keyPointer = [IntPtr]::Zero
try {
    $env:OPENAI_API_KEY = $null
    if ($Mode -eq 'Mock') {
        $env:LLM_API_KEY = 'mock-provider-not-a-real-key'
    } else {
        $secureKey = Get-Content -LiteralPath $keyFile -Raw | ConvertTo-SecureString
        $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
        $env:LLM_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    }
    # The child process inherits this environment; it is removed from this shell right after.
    $proc = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $javaArgs `
        -WorkingDirectory $backendPath -WindowStyle Hidden `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
}
finally {
    $env:LLM_API_KEY = $previous.LLM_API_KEY
    $env:OPENAI_API_KEY = $previous.OPENAI_API_KEY
    if ($keyPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer) }
    if ($secureKey) { $secureKey.Dispose() }
}

[ordered]@{ pid = $proc.Id; mode = $Mode; port = $Port; mockPort = $MockPort; maxOutputTokens = $MaxOutputTokens; monthlyBudgetUsd = $MonthlyBudgetUsd; dailyBudgetUsd = $DailyBudgetUsd; sessionBudgetUsd = $SessionBudgetUsd; requestTimeoutMs = $RequestTimeoutMs; searchBridgePort = $SearchBridgePort; started = (Get-Date).ToString('o'); stdout = $stdout } |
    ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8

Write-Host "Starting ($Mode) on http://127.0.0.1:$Port ... (pid $($proc.Id))"
$deadline = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $deadline) {
    if ($proc.HasExited) { throw "STOP: Server exited during startup. See $stderr" }
    try {
        $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 3
        if ($health.status) { Write-Host "READY: health=$($health.status), mode=$Mode, LLM=$llmUrl, maxOutputTokens=$MaxOutputTokens" -ForegroundColor Green; Write-Host "       budgets month/day/session = $MonthlyBudgetUsd / $DailyBudgetUsd / $SessionBudgetUsd USD, requestTimeoutMs = $RequestTimeoutMs" -ForegroundColor Green; Write-Host ("       lawyer search = " + $(if ($SearchBridgePort -gt 0) { "OpenAI web search bridge $searchUrl (paid, cap in the bridge window)" } else { "NAVER mock (free)" })) -ForegroundColor Green; exit 0 }
    } catch { Start-Sleep -Seconds 2 }
}
throw "STOP: Server did not become ready in 90s. See $stderr"
