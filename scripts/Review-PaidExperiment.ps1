# Local application authentication only. Never reads or accepts an OpenAI/Naver API key.
[CmdletBinding()]
param(
    [ValidateSet('Snapshot','Cost','Search')][string]$Mode = 'Snapshot',
    [string]$ServerUrl,
    [long]$IntakeId,
    [long]$SearchRequestId,
    [switch]$Execute,
    [ValidateSet('unknown','mock','actual')][string]$ProviderMode = 'unknown',
    [ValidateSet('unknown','mock','actual')][string]$GuardMode = 'unknown',
    [string]$Before,
    [string]$After,
    [string]$Receipt,
    [string]$ProviderUsage,
    [string]$OutputDirectory,
    [string]$Username = 'user',
    [string]$MetricsUsername = 'admin',
    [ValidateRange(1,600)][int]$WatchSeconds = 180,
    [switch]$UsePresentationCredentials
)
$ErrorActionPreference = 'Stop'
$ProviderMode = $ProviderMode.ToLowerInvariant()
$GuardMode = $GuardMode.ToLowerInvariant()
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskScript = Join-Path $PSScriptRoot 'Review-PaidExperiment.mjs'
$taskNode = (Get-Command node.exe).Source
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $taskRoot ('output/paid-experiment-review/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')) }
$taskOldEnv = @{}
foreach ($taskName in @('EXPERIMENT_APP_JWT','EXPERIMENT_METRICS_USER','EXPERIMENT_METRICS_PASSWORD')) {
    $taskOldEnv[$taskName] = [Environment]::GetEnvironmentVariable($taskName,'Process')
    [Environment]::SetEnvironmentVariable($taskName,$null,'Process')
}
function Read-TransientPassword([string]$Label) {
    $taskSecure = Read-Host $Label -AsSecureString
    $taskPointer = [IntPtr]::Zero
    try { $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure); return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer) }
    finally { if ($taskPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer) }; $taskSecure.Dispose() }
}
function Invoke-ReviewNode([string[]]$NodeArguments) {
    & $taskNode $taskScript @NodeArguments
    if ($LASTEXITCODE) { throw 'REVIEW_STOP: verification tool failed; no automatic retry was performed.' }
}
try {
    if ($Mode -eq 'Cost') {
        if ($Execute -or -not $Before -or -not $After) { throw 'REVIEW_STOP: Cost requires before/after files and never executes a call.' }
        $taskArgs = @('--action','cost','--before',$Before,'--after',$After,'--output',$OutputDirectory)
        if ($Receipt) { $taskArgs += @('--receipt',$Receipt) }
        if ($ProviderUsage) { $taskArgs += @('--provider-usage',$ProviderUsage) }
        Invoke-ReviewNode -NodeArguments $taskArgs
        return
    }
    $taskUri = [Uri]$ServerUrl
    if (-not $taskUri.IsAbsoluteUri -or $taskUri.Scheme -notin @('http','https') -or $taskUri.Host -notin @('127.0.0.1','localhost','[::1]','::1') -or $taskUri.UserInfo -or $taskUri.Query -or $taskUri.Fragment -or $taskUri.AbsolutePath -ne '/') {
        throw 'REVIEW_STOP: specify an exact loopback application server origin.'
    }
    if ($Execute -and ($Mode -ne 'Search' -or $ProviderMode -eq 'unknown' -or $GuardMode -eq 'unknown')) { throw 'REVIEW_STOP: execution requires Search plus explicit provider and Guard modes.' }
    if ($UsePresentationCredentials -and ($ServerUrl.TrimEnd('/') -ne 'http://127.0.0.1:18080' -or ($Execute -and ($ProviderMode -ne 'mock' -or $GuardMode -ne 'mock')))) {
        throw 'REVIEW_STOP: presentation credentials are limited to the isolated 18080 mock demo.'
    }
    $env:EXPERIMENT_METRICS_USER = $MetricsUsername
    $env:EXPERIMENT_METRICS_PASSWORD = if ($UsePresentationCredentials) { 'admin123' } else { Read-TransientPassword 'Local Actuator ADMIN password (not an API key)' }
    if ($Mode -eq 'Snapshot') {
        Invoke-ReviewNode -NodeArguments @('--action','snapshot','--base',$ServerUrl,'--output',$OutputDirectory)
        return
    }
    if ($IntakeId -le 0 -or $SearchRequestId -le 0) { throw 'REVIEW_STOP: use saved positive intake/search request IDs.' }
    $taskPassword = if ($UsePresentationCredentials) { 'user123' } else { Read-TransientPassword 'Local application account password (not an API key)' }
    try {
        $taskPayload = @{username=$Username;password=$taskPassword} | ConvertTo-Json -Compress
        $taskLogin = Invoke-RestMethod -Uri ($ServerUrl.TrimEnd('/') + '/api/auth/login') -Method Post -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($taskPayload)) -MaximumRedirection 0 -TimeoutSec 15
        if (-not $taskLogin.accessToken) { throw 'No application token' }
        $env:EXPERIMENT_APP_JWT = [string]$taskLogin.accessToken
    } finally { $taskPassword = $null; $taskPayload = $null; $taskLogin = $null }
    $taskArgs = @('--action','search','--base',$ServerUrl,'--intake-id',[string]$IntakeId,'--search-request-id',[string]$SearchRequestId,'--provider-mode',$ProviderMode,'--guard-mode',$GuardMode,'--watch-seconds',[string]$WatchSeconds)
    if ($Execute) {
        Invoke-ReviewNode -NodeArguments ($taskArgs + @('--output',(Join-Path $OutputDirectory 'preview')))
        $taskPreview = Get-Content -LiteralPath (Join-Path $OutputDirectory 'preview/search-review.json') -Raw | ConvertFrom-Json
        if ($taskPreview.status -ne 'PREVIEW_READY') { throw 'REVIEW_STOP: only a new PREVIEW_READY request can execute.' }
        Write-Host "Server: $ServerUrl / intake: $IntakeId / search request: $SearchRequestId / provider: $ProviderMode / Guard: $GuardMode"
        Write-Host 'One P03 request can trigger query generation, two Naver searches, explanations and Guard calls.'
        Write-Host 'No automatic POST retry. This tool is not a billing cap; existing server budgets apply.'
        $taskConfirmation = if ($ProviderMode -eq 'actual') { 'ONE_SEARCH_MAY_BILL' } else { 'MOCK_ONLY' }
        if ((Read-Host "Type $taskConfirmation to submit this single execution") -cne $taskConfirmation) { Write-Host 'Cancelled. No execution request sent.'; return }
        $taskArgs += @('--execute','--confirm',$taskConfirmation)
    }
    Invoke-ReviewNode -NodeArguments ($taskArgs + @('--output',$OutputDirectory))
} catch {
    if ($_.Exception.Message.StartsWith('REVIEW_STOP:')) { Write-Error $_.Exception.Message -ErrorAction Continue }
    else { Write-Error 'REVIEW_STOP: local authentication or file verification failed. No raw response or credential is displayed.' -ErrorAction Continue }
    exit 1
} finally {
    foreach ($taskName in $taskOldEnv.Keys) { [Environment]::SetEnvironmentVariable($taskName,$taskOldEnv[$taskName],'Process') }
}
