# Screen 10 (마케팅 집계, C13) seed experiment. Cost 0: Mock mode only; search requests are only stored, never executed.
#
# A dummy client journey of 40 visitor sessions, in 3 waves (refresh the screen between waves):
#   wave 1  VISIT 40 · INTAKE_STARTED 28                                (reported events, public P06)
#   wave 2  20 intakes (user/user2, varied case type · next step · region) · SUMMARY_VIEWED 16
#   wave 3  search requests 13 on 10 intakes · admin status: 6 -> CONSULTATION_READY, 1 of them -> CLOSED, 1 REVIEWED, 1 NEED_MORE_INFO
# plus integrity checks (duplicates, mismatched session, other user's intake, closed intake, access control).
# All checks compare BEFORE/AFTER deltas, so existing data on the server does not matter.
#
# Requires: mock provider (window A) and .\scripts\Start-LlmTestServer.ps1 -Mode Mock -Port 8080 (window B).
# Screen: http://127.0.0.1:5176/operator.html#/marketing  (server address in the screen: http://127.0.0.1:8080)
# Results: backend/guardrail-ready-policy-checker/.local/llm-test/screen10-<timestamp>.json
[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)][int]$Port = 8080,
    [ValidateRange(0, 600)][int]$GapSeconds = 20
)
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local/llm-test'
$stateFile = Join-Path $runDirectory 'server.json'
$script:base = "http://127.0.0.1:$Port"
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$results = New-Object System.Collections.Generic.List[object]

if (-not (Test-Path -LiteralPath $stateFile)) { throw 'STOP: no test server state. Start-LlmTestServer.ps1 -Mode Mock -Port 8080 first.' }
$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
if ($state.mode -ne 'Mock') { throw "STOP: test server is in $($state.mode) mode. This experiment runs only in Mock mode." }
if ([int]$state.port -ne $Port) { throw "STOP: test server runs on $($state.port), not $Port." }

Add-Type -AssemblyName System.Net.Http
$http = [Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds(40)

function Call([string]$method, [string]$path, $body = $null, [string]$token = $null, [hashtable]$headers = $null) {
    $req = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($method), $script:base + $path)
    if ($token) { $req.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token) }
    if ($headers) { foreach ($k in $headers.Keys) { [void]$req.Headers.TryAddWithoutValidation($k, [string]$headers[$k]) } }
    if ($null -ne $body) {
        $json = $body | ConvertTo-Json -Depth 8 -Compress
        $req.Content = [Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $res = $http.SendAsync($req).GetAwaiter().GetResult()
        $text = [Text.Encoding]::UTF8.GetString($res.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
        $data = $null; if ($text) { try { $data = $text | ConvertFrom-Json } catch { } }
        return [pscustomobject]@{ status = [int]$res.StatusCode; data = $data }
    } finally { $req.Dispose() }
}
function Login([string]$u, [string]$p) {
    $r = Call 'POST' '/api/auth/login' @{ username = $u; password = $p }
    if ($r.status -ne 200) { throw "login failed for $u (HTTP $($r.status))" }
    return $r.data.accessToken
}
function Check([string]$id, [string]$what, [bool]$pass, [string]$evidence) {
    $results.Add([pscustomobject]@{ id = $id; check = $what; result = $(if ($pass) { 'PASS' } else { 'FAIL' }); evidence = $evidence })
    Write-Host ("[{0}] {1,-4} {2} :: {3}" -f $(if ($pass) { 'PASS' } else { 'FAIL' }), $id, $what, $evidence) -ForegroundColor $(if ($pass) { 'Green' } else { 'Red' })
}
function Funnel { $r = Call 'GET' '/api/admin/dashboard/marketing-funnel' $null $script:admin; if ($r.status -ne 200) { throw "C13 HTTP $($r.status)" }; return $r.data }
function Stage($f, [string]$name) { return [long](@($f.stages | Where-Object { $_.stage -eq $name })[0].count) }
function Dist($map, [string]$key) { $p = $map.PSObject.Properties[$key]; if ($p) { return [long]$p.Value } else { return 0 } }
function Event([string]$id, [string]$type, [string]$session, $intakeId = $null) {
    $b = [ordered]@{ eventId = $id; eventType = $type; sessionId = $session }
    if ($null -ne $intakeId) { $b.intakeId = $intakeId }
    return Call 'POST' '/api/intake-events' $b
}
function Pause-Wave([string]$next) {
    if ($GapSeconds -gt 0) { Write-Host ("... {0}: refresh the marketing screen now. {1} starts in {2}s" -f (Get-Date -Format 'HH:mm:ss'), $next, $GapSeconds) -ForegroundColor DarkGray; Start-Sleep -Seconds $GapSeconds }
}

# 20 dummy intakes: case type · next step · region (deterministic, so the expected distribution is known)
$caseTypes = @('CIVIL','CIVIL','FAMILY','CIVIL','CRIMINAL','CIVIL','FAMILY','ELECTRONIC_LITIGATION','CIVIL','UNKNOWN','CIVIL','CRIMINAL','FAMILY','CIVIL','CIVIL','ELECTRONIC_LITIGATION','CRIMINAL','FAMILY','CIVIL','UNKNOWN')
$nextSteps = @('절차 안내','변호사 상담','절차 안내','서류 작성','변호사 상담','절차 안내','변호사 상담','서류 작성','절차 안내','잘 모르겠어요','변호사 상담','변호사 상담','절차 안내','절차 안내','서류 작성','절차 안내','변호사 상담','변호사 상담','절차 안내','잘 모르겠어요')
$regions   = @('서울','서울','경기','서울','부산','경기','서울','서울','부산','광주','서울','경기','대구','서울','경기','서울','부산','경기','서울','광주')
$statements = @{ CIVIL = '빌려준 돈을 약속한 날짜가 지나도 돌려받지 못했습니다.'; FAMILY = '이혼 후 양육비가 몇 달째 들어오지 않고 있습니다.'
    CRIMINAL = '중고거래 사기를 당해 고소 절차를 알고 싶습니다.'; ELECTRONIC_LITIGATION = '전자소송으로 소장을 제출하는 방법을 알고 싶습니다.'; UNKNOWN = '어떤 절차를 밟아야 할지 잘 모르겠습니다.' }
$intakes = @{}

try {
    if ((Call 'GET' '/actuator/health').status -ne 200) { throw 'STOP: test server is not healthy.' }
    $script:admin = Login 'admin' 'admin123'
    $user = Login 'user' 'user123'; $user2 = Login 'user2' 'user2123'; $lawyer = Login 'lawyer' 'lawyer123'
    Write-Host "=== Screen 10 marketing seed (Mock, cost 0) on $base ===" -ForegroundColor Yellow

    # ---------------------------------------------------------------- access control on C13
    $r = Call 'GET' '/api/admin/dashboard/marketing-funnel'
    Check 'A1' 'C13 without JWT is refused' ($r.status -eq 401) "HTTP $($r.status)"
    $r = Call 'GET' '/api/admin/dashboard/marketing-funnel' $null $user
    Check 'A2' 'C13 with USER JWT is refused' ($r.status -eq 403) "HTTP $($r.status)"
    $r = Call 'GET' '/api/admin/dashboard/marketing-funnel' $null $lawyer
    Check 'A3' 'C13 with LAWYER JWT is refused' ($r.status -eq 403) "HTTP $($r.status)"
    $before = Funnel

    # ---------------------------------------------------------------- wave 1: top of the funnel (public reported events)
    Write-Host ("--- wave 1 VISIT/INTAKE_STARTED {0}" -f (Get-Date -Format 'HH:mm:ss')) -ForegroundColor Cyan
    $bad = 0
    for ($i = 1; $i -le 40; $i++) {
        $s = "s10-$stamp-{0:D2}" -f $i
        if ((Event "$s-visit" 'VISIT' $s).status -ne 201) { $bad++ }
        if ($i -le 28 -and (Event "$s-start" 'INTAKE_STARTED' $s).status -ne 201) { $bad++ }
    }
    Check 'W1' '40 VISIT + 28 INTAKE_STARTED accepted without login (public P06)' ($bad -eq 0) "non-201 responses=$bad"
    $dup = @(1..3 | ForEach-Object { Event ("s10-$stamp-01-visit") 'VISIT' ("s10-$stamp-01") })
    Check 'E1' 'same event resent 3x -> 200 created=false (no double count)' (@($dup | Where-Object { $_.status -eq 200 -and -not $_.data.created }).Count -eq 3) (($dup | ForEach-Object { "$($_.status)/$($_.data.created)" }) -join ' ')
    $r = Event "s10-$stamp-01-visit" 'INTAKE_STARTED' "s10-$stamp-01"
    Check 'E2' 'same eventId with a different payload -> 409' ($r.status -eq 409) "HTTP $($r.status)"
    Pause-Wave 'wave 2'

    # ---------------------------------------------------------------- wave 2: intakes + summary views
    Write-Host ("--- wave 2 intakes/SUMMARY_VIEWED {0}" -f (Get-Date -Format 'HH:mm:ss')) -ForegroundColor Cyan
    $bad = 0
    for ($i = 1; $i -le 20; $i++) {
        $s = "s10-$stamp-{0:D2}" -f $i; $k = $i - 1
        $token = if ($i % 2) { $user } else { $user2 }
        $body = [ordered]@{ sessionId = $s; inquiryPurpose = $nextSteps[$k]; caseType = $caseTypes[$k]; partyPosition = '신청인'
            coreStatement = "[화면10 더미 $i] " + $statements[$caseTypes[$k]]; desiredNextStep = $nextSteps[$k]; preferredRegion = $regions[$k] }
        $r = Call 'POST' '/api/intakes' $body $token @{ 'Idempotency-Key' = "s10-$stamp-intake-$i" }
        if ($r.status -ne 201) { $bad++; Write-Host "intake $i HTTP $($r.status) $($r.data.code)" -ForegroundColor Red; continue }
        $intakes[$i] = [pscustomobject]@{ id = $r.data.id; session = $s; token = $token }
        if ($i -le 16 -and (Event "$s-summary" 'SUMMARY_VIEWED' $s $r.data.id).status -ne 201) { $bad++ }
    }
    Check 'W2' '20 intakes stored + 16 SUMMARY_VIEWED' ($bad -eq 0 -and $intakes.Count -eq 20) "intakes=$($intakes.Count) failures=$bad"
    $r = Event "s10-$stamp-x-summary1" 'SUMMARY_VIEWED' "s10-$stamp-01"
    Check 'E3' 'SUMMARY_VIEWED without intakeId -> 400' ($r.status -eq 400) "HTTP $($r.status)"
    $r = Event "s10-$stamp-x-summary2" 'SUMMARY_VIEWED' "s10-$stamp-99" $intakes[1].id
    Check 'E4' 'SUMMARY_VIEWED with another session for the intake -> 400' ($r.status -eq 400) "HTTP $($r.status)"
    $r = Event "s10-$stamp-x-summary3" 'SUMMARY_VIEWED' "s10-$stamp-01" 999999999
    Check 'E5' 'SUMMARY_VIEWED for an unknown intake -> 404' ($r.status -eq 404) "HTTP $($r.status)"
    Pause-Wave 'wave 3'

    # ---------------------------------------------------------------- wave 3: search requests + consultation status
    Write-Host ("--- wave 3 search requests/status {0}" -f (Get-Date -Format 'HH:mm:ss')) -ForegroundColor Cyan
    $bad = 0
    for ($i = 1; $i -le 10; $i++) {
        $n = if ($i -le 3) { 2 } else { 1 }
        for ($j = 1; $j -le $n; $j++) {
            $r = Call 'POST' "/api/intakes/$($intakes[$i].id)/search-requests" @{ searchPurpose = "화면10 더미 검색 $i-$j" } $intakes[$i].token
            if ($r.status -ne 201) { $bad++; Write-Host "search $i-$j HTTP $($r.status)" -ForegroundColor Red }
        }
    }
    Check 'W3a' '13 search requests on 10 intakes (stored only, not executed)' ($bad -eq 0) "failures=$bad"
    $r = Call 'POST' "/api/intakes/$($intakes[1].id)/search-requests" @{ searchPurpose = '남의 문의에 검색 요청' } $user2
    Check 'E6' 'search request on another user''s intake -> refused' ($r.status -in 403, 404) "HTTP $($r.status)"
    $bad = 0
    foreach ($i in 1..6) { if ((Call 'PATCH' "/api/intakes/$($intakes[$i].id)/status" @{ status = 'CONSULTATION_READY' } $admin).status -ne 200) { $bad++ } }
    if ((Call 'PATCH' "/api/intakes/$($intakes[6].id)/status" @{ status = 'CLOSED' } $admin).status -ne 200) { $bad++ }
    if ((Call 'PATCH' "/api/intakes/$($intakes[7].id)/status" @{ status = 'REVIEWED' } $admin).status -ne 200) { $bad++ }
    if ((Call 'PATCH' "/api/intakes/$($intakes[8].id)/status" @{ status = 'NEED_MORE_INFO' } $admin).status -ne 200) { $bad++ }
    Check 'W3b' 'status: 6 READY, then 1 of them CLOSED; 1 REVIEWED; 1 NEED_MORE_INFO' ($bad -eq 0) "failures=$bad"
    $r = Call 'PATCH' "/api/intakes/$($intakes[6].id)/status" @{ status = 'CONSULTATION_READY' } $admin
    Check 'E7' 'CLOSED intake cannot go back to READY -> 409' ($r.status -eq 409) "HTTP $($r.status) $($r.data.code)"
    $r = Call 'PATCH' "/api/intakes/$($intakes[9].id)/status" @{ status = 'CONSULTATION_READY' } $user
    Check 'E8' 'USER cannot change intake status' ($r.status -eq 403) "HTTP $($r.status)"

    # ---------------------------------------------------------------- verify C13 deltas
    $after = Funnel
    $expect = [ordered]@{ VISIT = 40; INTAKE_STARTED = 28; INTAKE_SUBMITTED = 20; SUMMARY_VIEWED = 16; LAWYER_SEARCH_REQUESTED = 10; CONSULTATION_READY = 5 }
    $rows = foreach ($k in $expect.Keys) { $d = (Stage $after $k) - (Stage $before $k); [pscustomobject]@{ stage = $k; before = (Stage $before $k); after = (Stage $after $k); delta = $d; expected = $expect[$k] } }
    $rows | Format-Table -AutoSize | Out-String | Write-Host
    Check 'V1' 'journey stages increased exactly as seeded' (@($rows | Where-Object { $_.delta -ne $_.expected }).Count -eq 0) (($rows | ForEach-Object { "$($_.stage)+$($_.delta)" }) -join ' ')
    $dReq = $after.searchRequestCount - $before.searchRequestCount; $dInt = $after.searchRequestedIntakeCount - $before.searchRequestedIntakeCount
    Check 'V2' 'search: 13 requests but 10 distinct intakes (different measures)' ($dReq -eq 13 -and $dInt -eq 10) "searchRequestCount +$dReq, searchRequestedIntakeCount +$dInt"
    $dReady = $after.consultationReadyIntakeCount - $before.consultationReadyIntakeCount
    Check 'V3' 'consultation ready = CURRENT status (6 set, 1 closed -> +5)' ($dReady -eq 5) "consultationReadyIntakeCount +$dReady"
    $distFail = @()
    foreach ($pair in @(@('caseTypeDistribution', $caseTypes), @('desiredNextStepDistribution', $nextSteps), @('regionDistribution', $regions))) {
        $name = $pair[0]; $groups = $pair[1] | Group-Object
        foreach ($g in $groups) { $d = (Dist $after.$name $g.Name) - (Dist $before.$name $g.Name); if ($d -ne $g.Count) { $distFail += "$name/$($g.Name) +$d (expected +$($g.Count))" } }
    }
    Check 'V4' 'case type / next step / region distributions match the 20 intakes' ($distFail.Count -eq 0) $(if ($distFail.Count) { $distFail -join '; ' } else { 'all buckets match' })
    Check 'V5' 'drop-off rate is not computed (measures differ)' ($after.dropOffMeasured -eq $false -and $after.measurementBasis -eq 'ALL_RECORDS_IN_CURRENT_DATABASE') "dropOffMeasured=$($after.dropOffMeasured) basis=$($after.measurementBasis)"

    $out = Join-Path $runDirectory "screen10-$stamp.json"
    ([ordered]@{ at = $stamp; base = $base; results = $results; before = $before; after = $after; stages = $rows } | ConvertTo-Json -Depth 10) | Set-Content -LiteralPath $out -Encoding UTF8
    $fail = @($results | Where-Object result -eq 'FAIL').Count
    Write-Host ''
    Write-Host ("=== {0}/{1} PASS, saved {2} ===" -f ($results.Count - $fail), $results.Count, $out) -ForegroundColor $(if ($fail) { 'Red' } else { 'Green' })
    Write-Host 'Now refresh http://127.0.0.1:5176/operator.html#/marketing and capture it.'
}
finally { $http.Dispose() }
