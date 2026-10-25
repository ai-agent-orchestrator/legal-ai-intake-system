# Step 2: ONE paid text generation, run by hand. No retry, no loop.
# Reads the DPAPI-encrypted key saved by Set-LocalOpenAiKey.ps1 (same Windows user, same PC).
# Prints only the reply, model, token usage and estimated cost. Never prints the key or raw error bodies.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

# ---- Fixed limits (edit here only on purpose) ----
$Model = 'gpt-4o-mini'
$MaxCompletionTokens = 16
$InputUsdPer1M = 0.15     # repo default for gpt-4o-mini ($0.00015 per 1K)
$OutputUsdPer1M = 0.60    # repo default for gpt-4o-mini ($0.00060 per 1K)
$PromptText = 'Reply with exactly one word: OK'
$MinSecondsBetweenRuns = 60
# ---------------------------------------------------

$projectRoot = Split-Path -Parent $PSScriptRoot
$secretDirectory = Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local/secrets'
$keyFile = Join-Path $secretDirectory 'openai-key.dpapi'
$logFile = Join-Path $secretDirectory 'paid-call-log.jsonl'
$stage = 'precheck'
$secureKey = $null
$keyPointer = [IntPtr]::Zero
$plainKey = $null
$httpClient = $null
$request = $null
$response = $null

try {
    if (-not (Test-Path -LiteralPath $keyFile)) { throw 'STOP: No saved key. Run Set-LocalOpenAiKey.ps1 first.' }

    # Guard against an accidental double run.
    if (Test-Path -LiteralPath $logFile) {
        $last = Get-Content -LiteralPath $logFile | Select-Object -Last 1 | ConvertFrom-Json
        $elapsed = ([DateTimeOffset]::Now - [DateTimeOffset]::Parse([string]$last.at)).TotalSeconds
        if ($elapsed -lt $MinSecondsBetweenRuns) {
            throw ("STOP: Last paid call was {0:N0}s ago. Wait {1}s between runs." -f $elapsed, $MinSecondsBetweenRuns)
        }
    }

    # Worst case: generous input estimate (system/format overhead) + full output cap.
    $worstInputTokens = 100
    $worstCost = ($worstInputTokens * $InputUsdPer1M + $MaxCompletionTokens * $OutputUsdPer1M) / 1000000
    Write-Host ''
    Write-Host '=== ONE paid OpenAI call ===' -ForegroundColor Yellow
    Write-Host "Model           : $Model"
    Write-Host "Max output      : $MaxCompletionTokens tokens"
    Write-Host "Prompt          : $PromptText"
    Write-Host ("Worst-case cost : about `${0:N6} (estimate)" -f $worstCost)
    Write-Host 'Retry           : none'
    Write-Host ''
    $answer = Read-Host 'Type y to send exactly one request (anything else cancels)'
    if ($answer -cne 'y') { Write-Host 'Cancelled. Nothing was sent.'; exit 0 }

    $stage = 'load-key'
    $secureKey = Get-Content -LiteralPath $keyFile -Raw | ConvertTo-SecureString
    $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)

    $stage = 'http-setup'
    Add-Type -AssemblyName System.Net.Http
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $httpClient = [Net.Http.HttpClient]::new($handler)
    $httpClient.Timeout = [TimeSpan]::FromSeconds(30)
    $body = [ordered]@{
        model = $Model
        max_completion_tokens = $MaxCompletionTokens
        store = $false
        messages = @(@{ role = 'user'; content = $PromptText })
    } | ConvertTo-Json -Depth 4
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, 'https://api.openai.com/v1/chat/completions')
    $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $plainKey)
    $request.Content = [Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, 'application/json')

    $stage = 'network-send'
    $sentAt = [DateTimeOffset]::Now
    # Logged BEFORE sending, so even a timeout/network error counts for the double-run guard.
    ([ordered]@{ at = $sentAt.ToString('o'); model = $Model; status = 'sending' } | ConvertTo-Json -Compress) | Add-Content -LiteralPath $logFile -Encoding UTF8
    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    $plainKey = $null
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()

    $stage = 'parse-response'
    $statusCode = [int]$response.StatusCode
    if (-not $response.IsSuccessStatusCode) {
        # Only the machine-readable code/type, not the message (which can include a masked key).
        $code = ''; $type = ''
        try { $err = ($text | ConvertFrom-Json).error; $code = $err.code; $type = $err.type } catch {}
        $entry = [ordered]@{ at = $sentAt.ToString('o'); model = $Model; status = $statusCode; errorCode = $code; errorType = $type }
        ($entry | ConvertTo-Json -Compress) | Add-Content -LiteralPath $logFile -Encoding UTF8
        throw "STOP: OpenAI HTTP $statusCode (code=$code, type=$type). No retry was attempted."
    }

    $payload = $text | ConvertFrom-Json
    $reply = $payload.choices[0].message.content
    $inTok = [int]$payload.usage.prompt_tokens
    $outTok = [int]$payload.usage.completion_tokens
    $cost = ($inTok * $InputUsdPer1M + $outTok * $OutputUsdPer1M) / 1000000

    $entry = [ordered]@{ at = $sentAt.ToString('o'); model = $payload.model; status = $statusCode; promptTokens = $inTok; completionTokens = $outTok; estimatedUsd = [Math]::Round($cost, 8) }
    ($entry | ConvertTo-Json -Compress) | Add-Content -LiteralPath $logFile -Encoding UTF8

    Write-Host ''
    Write-Host 'PAID CALL: SUCCESS' -ForegroundColor Green
    Write-Host "Reply           : $reply"
    Write-Host "Returned model  : $($payload.model)"
    Write-Host "Tokens in / out : $inTok / $outTok"
    Write-Host ("Estimated cost  : `${0:N8}" -f $cost)
    Write-Host 'Logged (no key) : .local/secrets/paid-call-log.jsonl'
}
catch {
    $message = $_.Exception.Message
    if ($message.StartsWith('STOP:')) {
        Write-Host $message -ForegroundColor Red
    } else {
        $types = @(); $ex = $_.Exception
        while ($ex) { $types += $ex.GetType().FullName; $ex = $ex.InnerException }
        Write-Host "Failed at stage: $stage" -ForegroundColor Red
        Write-Host ("Exception types: " + ($types -join ' > ')) -ForegroundColor Red
        Write-Host 'No key or error message was printed. No retry was attempted.' -ForegroundColor Red
    }
    exit 1
}
finally {
    if ($response) { $response.Dispose() }
    if ($request) { $request.Dispose() }
    if ($httpClient) { $httpClient.Dispose() }
    if ($keyPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer) }
    if ($secureKey) { $secureKey.Dispose() }
    $plainKey = $null
}
