[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$Port=8080)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$metadataPath=Join-Path $projectRoot "backend/guardrail-ready-policy-checker/.local/runtime-logs/server-$Port.json"
$metadata=Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$serverProcess=Get-Process -Id $metadata.pid -ErrorAction Stop
if($serverProcess.ProcessName -ne 'java' -or $serverProcess.StartTime.ToUniversalTime().Ticks -ne ([DateTime]$metadata.started).ToUniversalTime().Ticks){throw 'Managed server identity mismatch; nothing stopped.'}
$toolPath=Join-Path $projectRoot 'tmp/local-stop-tools'
New-Item -ItemType Directory -Path $toolPath -Force | Out-Null
$javaBin=Split-Path -Parent (Get-Command java.exe).Source
& "$javaBin/javac.exe" --add-modules jdk.attach -d $toolPath "$PSScriptRoot/local-db/GracefulExitAgent.java" "$PSScriptRoot/local-db/AttachSnapshot.java"
if($LASTEXITCODE){throw 'Cannot compile local stop helper.'}
[IO.File]::WriteAllText("$toolPath/MANIFEST.MF","Manifest-Version: 1.0`nAgent-Class: GracefulExitAgent`n`n",[Text.UTF8Encoding]::new($false))
& "$javaBin/jar.exe" --create --file "$toolPath/exit-agent.jar" --manifest "$toolPath/MANIFEST.MF" -C $toolPath GracefulExitAgent.class
if($LASTEXITCODE){throw 'Cannot package local stop helper.'}
& "$javaBin/java.exe" --add-modules jdk.attach -cp $toolPath AttachSnapshot $metadata.pid "$toolPath/exit-agent.jar" stop
if($LASTEXITCODE){throw 'Graceful stop request failed.'}
if(-not $serverProcess.WaitForExit(20000)){throw 'Server has not finished graceful shutdown; no forced kill was used.'}
[pscustomobject]@{stoppedPid=$metadata.pid;port=$Port;graceful=$true}|ConvertTo-Json
