# Sends ONE /api/ai/chat request to the LLM test server, then shows the cost ledger (Prometheus ai_cost_*).
# Uses the local demo accounts that are hard-coded in SecurityConfig (user / admin). No OpenAI key here.
#   -Message   : text to send (default: a short civil-procedure question)
#   -SessionId : reuse a session across runs to accumulate the per-session budget (default: new session)
#   -Yes       : skip the confirmation prompt. Allowed ONLY when the server runs in Mock mode.
[CmdletBinding()]
param(
    [ValidateLength(1, 200)][string]$Message = '민사 소송을 시작하려면 어떤 서류를 준비해야 하나요?',
    [ValidatePattern('^[A-Za-z0-9_-]{0,60}$')][string]$SessionId = '',
    [switch]$Yes
)
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local/llm-test'
$stateFile = Join-Path $runDirectory 'server.json'
$logFile = Join-Path $runDirectory 'app-call-log.jsonl'
$ledgerPattern = '^ai_cost_(actual|reserved|accounted|estimated_usage)_usd|^ai_cost_stage_(actual|estimated_usage)_usd\{.*stage="(answer|input_guardrail|output_guardrail)"|^ai_llm_tokens_total|^ai_cost_blocked_total'

function Show-CostLedger([string]$base, [string]$title) {
    $pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:admin123'))
    $text = (Invoke-WebRequest "$base/actuator/prometheus" -Headers @{ Authorization = "Basic $pair" } -UseBasicParsing -TimeoutSec 10).Content
    $lines = $text -split "`n" | Where-Object { $_ -match $ledgerPattern } |
        ForEach-Object { $_ -replace 'application="ai-agent-security-observability-lab",?', '' }
    Write-Host "--- cost ledger $title (USD = reported tokens x configured price; guard stages are MOCK) ---"
    if ($lines) { $lines | ForEach-Object { Write-Host $_ } } else { Write-Host '(no ai_cost lines yet)' }
}

try {
    if (-not (Test-Path -LiteralPath $stateFile)) { throw 'STOP: No LLM test server running. Start it with Start-LlmTestServer.ps1.' }
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $base = "http://127.0.0.1:$($state.port)"
    $paid = $state.mode -eq 'RealLlm'
    if ($Yes -and $paid) { throw 'STOP: -Yes is only allowed in Mock mode. Paid calls always need a typed y.' }

    # Paid calls: one per minute at most. Mock calls: a short pause only.
    $minSeconds = if ($paid) { 60 } else { 2 }
    if (Test-Path -LiteralPath $logFile) {
        $last = Get-Content -LiteralPath $logFile | Select-Object -Last 1 | ConvertFrom-Json
        $elapsed = ([DateTimeOffset]::Now - [DateTimeOffset]::Parse([string]$last.at)).TotalSeconds
        if ($elapsed -lt $minSeconds) { throw ("STOP: Last app call was {0:N0}s ago. Wait {1}s." -f $elapsed, $minSeconds) }
    }
    if (-not $SessionId) { $SessionId = 'llm-test-' + (Get-Date -Format 'yyyyMMdd-HHmmss') }

    Write-Host ''
    Write-Host '=== ONE app chat request (/api/ai/chat) ===' -ForegroundColor Yellow
    Write-Host "Server mode     : $($state.mode)  (guard = local mock, LLM = $(if ($paid) { 'REAL OpenAI gpt-4o-mini' } else { 'local mock' }))"
    Write-Host "Max output      : $($state.maxOutputTokens) tokens"
    Write-Host "Budgets m/d/s   : $($state.monthlyBudgetUsd) / $($state.dailyBudgetUsd) / $($state.sessionBudgetUsd) USD   timeout $($state.requestTimeoutMs) ms"
    Write-Host "Session         : $SessionId"
    Write-Host "Message ($($Message.Length) chars): $Message"
    Write-Host ("Cost            : {0}" -f $(if ($paid) { 'PAID if it reaches generation - about $0.0001 or less' } else { '0 (mock)' }))
    Write-Host 'Retry           : none'
    Write-Host ''
    Show-CostLedger $base '(before)'
    Write-Host ''
    if (-not $Yes) {
        $answer = Read-Host 'Type y to send exactly one request (anything else cancels)'
        if ($answer -cne 'y') { Write-Host 'Cancelled. Nothing was sent.'; exit 0 }
    }

    $login = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body '{"username":"user","password":"user123"}' -TimeoutSec 10
    $body = [Text.Encoding]::UTF8.GetBytes((@{ message = $Message; sessionId = $SessionId } | ConvertTo-Json))

    $at = [DateTimeOffset]::Now
    ([ordered]@{ at = $at.ToString('o'); mode = $state.mode; sessionId = $SessionId; status = 'sending' } | ConvertTo-Json -Compress) | Add-Content -LiteralPath $logFile -Encoding UTF8
    try {
        # Decode as UTF-8 explicitly: Windows PowerShell 5.1 assumes ISO-8859-1 when no charset is sent.
        $raw = Invoke-WebRequest "$base/api/ai/chat" -Method Post -ContentType 'application/json; charset=utf-8' `
            -Headers @{ Authorization = "Bearer $($login.accessToken)" } -Body $body -TimeoutSec 60 -UseBasicParsing
        $httpStatus = [int]$raw.StatusCode
        $res = [Text.Encoding]::UTF8.GetString($raw.RawContentStream.ToArray()) | ConvertFrom-Json
    } catch [System.Net.WebException] {
        # 4xx/5xx from the app (e.g. validation): show the status and the error code, then the ledger.
        $httpStatus = [int]$_.Exception.Response.StatusCode
        $reader = [IO.StreamReader]::new($_.Exception.Response.GetResponseStream(), [Text.Encoding]::UTF8)
        $res = $null
        try { $res = $reader.ReadToEnd() | ConvertFrom-Json } catch { $res = $null } finally { $reader.Dispose() }
    }

    ([ordered]@{ at = $at.ToString('o'); mode = $state.mode; sessionId = $SessionId; http = $httpStatus; blocked = $res.blocked; fallback = $res.fallback; reasonCode = $res.reasonCode; detailCode = $res.detailCode; code = $res.code; model = $res.model; costSource = $res.costSource } | ConvertTo-Json -Compress) | Add-Content -LiteralPath $logFile -Encoding UTF8

    Write-Host ''
    $color = if ($httpStatus -ne 200 -or $res.blocked -or $res.fallback) { 'Red' } else { 'Green' }
    Write-Host "APP CHAT RESULT (HTTP $httpStatus)" -ForegroundColor $color
    if ($httpStatus -ne 200) { Write-Host "error code         : $($res.code)" }
    Write-Host "blocked / fallback : $($res.blocked) / $($res.fallback)"
    Write-Host "reasonCode/detail  : $($res.reasonCode) / $($res.detailCode)"
    Write-Host "model              : $($res.model)"
    Write-Host "usage / costSource : $($res.usageAvailable) / $($res.costSource)"
    Write-Host "traceId            : $($res.traceId)"
    Write-Host "answer             : $($res.answer)"
    Write-Host ''
    Show-CostLedger $base '(after)'
}
catch {
    $m = $_.Exception.Message
    if ($m.StartsWith('STOP:')) { Write-Host $m -ForegroundColor Red }
    else {
        Write-Host "Request failed. Exception: $($_.Exception.GetType().FullName)" -ForegroundColor Red
        Write-Host 'No retry was attempted.' -ForegroundColor Red
    }
    exit 1
}
