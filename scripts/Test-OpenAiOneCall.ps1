[CmdletBinding()]
param([switch]$ListModels,[string]$Model,[decimal]$InputUsdPer1M=-1,[decimal]$OutputUsdPer1M=-1,[ValidateRange(16,256)][int]$MaxOutputTokens=128,[decimal]$MaxEstimatedUsd=0.01)
$ErrorActionPreference='Stop'
$plain=$null; $secure=$null; $pointer=[IntPtr]::Zero; $client=$null
$projectRoot=Split-Path -Parent $PSScriptRoot
try {
    $plain=$env:LLM_API_KEY
    if(-not $plain){$plain=$env:OPENAI_API_KEY}
    $encrypted=Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local/openai-key.dpapi'
    if(-not $plain -and (Test-Path -LiteralPath $encrypted)){
        $secure=Get-Content -LiteralPath $encrypted -Raw | ConvertTo-SecureString
        $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        $plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    if(-not $plain){Write-Output 'No key is available to this process. Use Set-LocalOpenAiKey.ps1 locally; never paste the key into chat.';exit 2}
    if(-not $ListModels -and (-not $Model -or $InputUsdPer1M -lt 0 -or $OutputUsdPer1M -lt 0)){throw 'Specify the exact accessible model and verified per-1M input/output rates before generation.'}
    $estimate=($InputUsdPer1M*1000+$OutputUsdPer1M*$MaxOutputTokens)/1000000
    if(-not $ListModels -and ($MaxEstimatedUsd -le 0 -or $estimate -gt $MaxEstimatedUsd)){throw 'Estimated single-call reservation exceeds the configured smoke-test allowance.'}
    Add-Type -AssemblyName System.Net.Http
    $handler=[Net.Http.HttpClientHandler]::new();$handler.AllowAutoRedirect=$false
    $client=[Net.Http.HttpClient]::new($handler);$client.Timeout=[TimeSpan]::FromSeconds(30)
    $client.DefaultRequestHeaders.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$plain)
    if($ListModels){
        $response=$client.GetAsync('https://api.openai.com/v1/models').GetAwaiter().GetResult()
        if(-not $response.IsSuccessStatusCode){Write-Output ('Model authentication/list HTTP '+[int]$response.StatusCode+'; provider body suppressed.');exit 3}
        $models=($response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json).data.id|Sort-Object
        [pscustomobject]@{authentication='passed';generationCalls=0;models=$models}|ConvertTo-Json -Depth 3
    }else{
        $payload=@{model=$Model;max_completion_tokens=$MaxOutputTokens;messages=@(@{role='system';content='Answer in one short sentence. This is a connectivity test; use no personal data.'},@{role='user';content='Say that the connection test succeeded.'})}|ConvertTo-Json -Depth 5
        $content=[Net.Http.StringContent]::new($payload,[Text.Encoding]::UTF8,'application/json')
        # Exactly one generation, no automatic retry, official HTTPS host only.
        $response=$client.PostAsync('https://api.openai.com/v1/chat/completions',$content).GetAwaiter().GetResult()
        if(-not $response.IsSuccessStatusCode){Write-Output ('Generation HTTP '+[int]$response.StatusCode+'; no retry; provider body suppressed.');exit 4}
        $data=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json
        $usageAvailable=($null -ne $data.usage.prompt_tokens -and $null -ne $data.usage.completion_tokens)
        $result=[ordered]@{checkedAt=[DateTimeOffset]::Now.ToString('o');requestedModel=$Model;returnedModel=$data.model;generationCalls=1;usageAvailable=$usageAvailable;promptTokens=$data.usage.prompt_tokens;completionTokens=$data.usage.completion_tokens;configuredRateCostUsd=$(if($usageAvailable){($data.usage.prompt_tokens*$InputUsdPer1M+$data.usage.completion_tokens*$OutputUsdPer1M)/1000000}else{$null});appGuardConnection='not-tested';providerInvoiceReconciled=$false;note='Direct OpenAI connectivity only. Configured-rate usage cost, not an invoice. Reasoning tokens may be included in completion usage.'}
        $out=Join-Path $projectRoot 'output/presentation-2026-10-25';New-Item -ItemType Directory -Path $out -Force|Out-Null
        $result|ConvertTo-Json|Set-Content -LiteralPath "$out/openai-one-call.json" -Encoding utf8
        $result|ConvertTo-Json
    }
}catch{
    if($_.Exception.Message.StartsWith('Specify the exact') -or $_.Exception.Message.StartsWith('Estimated single-call')){Write-Output $_.Exception.Message}else{Write-Output 'Connection test failed; no retry; secret and provider error body suppressed.'}
    exit 1
}finally{
    if($client){$client.Dispose()};if($pointer -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)};if($secure){$secure.Dispose()};$plain=$null
}
