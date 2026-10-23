# Step 1 only: authenticate and list models. Never requests text generation.
# The key is encrypted with Windows DPAPI for this Windows user on this PC.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$VerbosePreference = 'SilentlyContinue'
$DebugPreference = 'SilentlyContinue'
$InformationPreference = 'SilentlyContinue'

$projectRoot = Split-Path -Parent $PSScriptRoot
$secretDirectory = Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local'
$keyFile = Join-Path $secretDirectory 'openai-key.dpapi'
$readinessFile = Join-Path $secretDirectory 'openai-readiness.json'
$secretKey = $null
$plainKey = $null
$keyPointer = [IntPtr]::Zero
$httpClient = $null
$request = $null
$response = $null

try {
    Write-Host 'Paste your OpenAI API key below. Input is hidden. Do not use a usage-page URL.'
    $secretKey = Read-Host 'OpenAI API key' -AsSecureString
    if ($secretKey.Length -eq 0) { throw 'No key supplied. Nothing was saved.' }
    $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secretKey)
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    if ($plainKey -ne $plainKey.Trim() -or $plainKey -match '[\r\n]' -or -not $plainKey.StartsWith('sk-')) {
        throw 'Invalid key format. Paste the API key only; no URL, spaces or quotes.'
    }

    Add-Type -AssemblyName System.Net.Http
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    # No custom host, redirect, retry or provider-response logging.
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $httpClient = [Net.Http.HttpClient]::new($handler)
    $httpClient.Timeout = [TimeSpan]::FromSeconds(20)
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, 'https://api.openai.com/v1/models')
    $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $plainKey)
    Write-Host 'Checking authentication and model list (no generation request)...'
    $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        $statusCode = [int]$response.StatusCode
        switch ($statusCode) {
            401 { throw 'HTTP 401: key authentication failed. Check the project key or revocation status.' }
            403 { throw 'HTTP 403: project/key lacks permission to list models. Ask the project administrator.' }
            429 { throw 'HTTP 429: provider limit reached. No automatic retry was attempted.' }
            default { throw "OpenAI HTTP $statusCode. No key or provider error body was printed." }
        }
    }
    $payload = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    $modelIds = @($payload.data | ForEach-Object { $_.id })
    if ($modelIds.Count -eq 0) { throw 'No models returned. Nothing was saved.' }
    $candidates = @('gpt-5-nano', 'gpt-4.1-nano', 'gpt-6-luna', 'gpt-4o-mini')
    $availability = @($candidates | ForEach-Object {
        [pscustomobject]@{ model = $_; listed = ($modelIds -contains $_) }
    })

    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    # Remove inherited access and restrict this directory to the current user.
    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $directoryAcl = [Security.AccessControl.DirectorySecurity]::new()
    $directoryAcl.SetOwner($currentSid)
    $directoryAcl.SetAccessRuleProtection($true, $false)
    $rule = [Security.AccessControl.FileSystemAccessRule]::new($currentSid,
        [Security.AccessControl.FileSystemRights]::FullControl,
        [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit',
        [Security.AccessControl.PropagationFlags]::None,
        [Security.AccessControl.AccessControlType]::Allow)
    $directoryAcl.AddAccessRule($rule)
    Set-Acl -LiteralPath $secretDirectory -AclObject $directoryAcl
    $encryptedKey = ConvertFrom-SecureString -SecureString $secretKey
    [IO.File]::WriteAllText($keyFile, $encryptedKey)
    $result = [ordered]@{
        checkedAt = [DateTimeOffset]::Now.ToString('o')
        authentication = 'passed'
        modelList = $availability
        generationTest = 'not-run'
        applicationConnection = 'not-configured'
        note = 'A listed model still needs a generation test; this does not prove billing credit or inference permission.'
    }
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $readinessFile -Encoding UTF8
    Write-Host 'Authentication: PASS. Key saved encrypted locally; not added to Git.'
    $availability | Format-Table -AutoSize
    Write-Host 'Step 1 complete. Tell Codex only that it completed; never send the key.'
    Write-Host 'Generation and the Spring/Guard connection have NOT been tested yet.'
}
catch {
    # Print only controlled local messages, never raw network exceptions/provider bodies.
    $safeMessages = @('No key supplied', 'Invalid key format', 'HTTP 401:', 'HTTP 403:', 'HTTP 429:', 'OpenAI HTTP ', 'No models returned')
    $message = $_.Exception.Message
    if (@($safeMessages | Where-Object { $message.StartsWith($_) }).Count -gt 0) {
        Write-Host $message -ForegroundColor Red
    } else {
        Write-Host 'Setup failed (network, response or local storage). No secret/error body printed. Tell Codex this message.' -ForegroundColor Red
    }
    exit 1
}
finally {
    if ($response) { $response.Dispose() }
    if ($request) { $request.Dispose() }
    if ($httpClient) { $httpClient.Dispose() }
    if ($keyPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer) }
    if ($secretKey) { $secretKey.Dispose() }
    $plainKey = $null
}
