[CmdletBinding()]
param(
    [ValidateRange(1024,65535)][int]$Port = 8080,
    [Parameter(Mandatory=$true)][string]$DatabasePath,
    [switch]$Initialize,
    [switch]$DisableExternalProviders
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$backendPath=Join-Path $projectRoot 'backend/guardrail-ready-policy-checker'
$privateRoot=[IO.Path]::GetFullPath((Join-Path $backendPath '.local'))
$databaseFile=[IO.Path]::GetFullPath($DatabasePath)
if(-not $databaseFile.StartsWith($privateRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Database must be below the backend .local directory.'}
if($Initialize -and (Test-Path -LiteralPath "$databaseFile.mv.db")){throw 'Initialization requires a new database file.'}
if(-not $Initialize -and -not (Test-Path -LiteralPath "$databaseFile.mv.db")){throw 'Existing migrated database required. Use a separate initialization/restore procedure first.'}
$builtJar=Join-Path $backendPath 'build/libs/ai-agent-security-observability-lab-0.0.1-SNAPSHOT.jar'
$jarHash=(Get-FileHash -LiteralPath $builtJar -Algorithm SHA256).Hash.ToLowerInvariant()
$releasePath=Join-Path $privateRoot 'releases'
$logPath=Join-Path $privateRoot 'runtime-logs'
New-Item -ItemType Directory -Path $releasePath,$logPath,(Split-Path -Parent $databaseFile) -Force | Out-Null
# Build outputs are never used as a running executable again.
$releaseJar=Join-Path $releasePath "legal-intake-$jarHash.jar"
if(-not (Test-Path -LiteralPath $releaseJar)){Copy-Item -LiteralPath $builtJar -Destination $releaseJar}
if((Get-FileHash -LiteralPath $releaseJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $jarHash){throw 'Release JAR hash mismatch.'}
$runStamp=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$javaArgs=@('-jar',$releaseJar,'--server.address=127.0.0.1',"--server.port=$Port",'--spring.profiles.active=local-persistent')
if($Initialize){$javaArgs+='--spring.jpa.hibernate.ddl-auto=update'}
if($DisableExternalProviders){$javaArgs+=@('--llm.api-key=','--llm.base-url=http://127.0.0.1:1','--guardrails.base-url=http://127.0.0.1:1','--search.enabled=false')}
$previousDbPath=$env:LOCAL_H2_DB_PATH
try{
    $env:LOCAL_H2_DB_PATH=$databaseFile.Replace('\','/')
    $serverProcess=Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $javaArgs -WorkingDirectory $backendPath -WindowStyle Hidden -RedirectStandardOutput "$logPath\$Port-$runStamp.stdout.log" -RedirectStandardError "$logPath\$Port-$runStamp.stderr.log" -PassThru
}finally{$env:LOCAL_H2_DB_PATH=$previousDbPath}
$metadata=[ordered]@{pid=$serverProcess.Id;started=$serverProcess.StartTime.ToUniversalTime().ToString('o');port=$Port;profile='local-persistent';jar=$releaseJar;jarSha256=$jarHash;databasePath=$databaseFile;initialize=[bool]$Initialize;externalProvidersDisabled=[bool]$DisableExternalProviders;stdout="$logPath\$Port-$runStamp.stdout.log";stderr="$logPath\$Port-$runStamp.stderr.log"}
$metadata | ConvertTo-Json | Set-Content -LiteralPath "$logPath\server-$Port.json" -Encoding utf8
$metadata | ConvertTo-Json
