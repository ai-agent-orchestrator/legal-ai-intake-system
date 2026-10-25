[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$privateDir=Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local/presentation-2026-10-25'
$metadata=Get-Content -LiteralPath "$privateDir/process.json" -Raw|ConvertFrom-Json
$backendProcess=Get-Process -Id $metadata.backendPid -ErrorAction Stop
$nodeProcess=Get-Process -Id $metadata.nodePid -ErrorAction Stop
$started=([DateTime]$metadata.started).ToUniversalTime()
if($backendProcess.ProcessName -ne 'java' -or $nodeProcess.ProcessName -ne 'node' -or [Math]::Abs(($backendProcess.StartTime.ToUniversalTime()-$started).TotalSeconds) -gt 10 -or [Math]::Abs(($nodeProcess.StartTime.ToUniversalTime()-$started).TotalSeconds) -gt 10){throw 'Demo process identity changed; nothing stopped.'}
$tools=Join-Path $projectRoot 'tmp/local-stop-tools'
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot/bin/java.exe' --add-modules jdk.attach -cp $tools AttachSnapshot $metadata.backendPid "$tools/exit-agent.jar" stop
if($LASTEXITCODE -or -not $backendProcess.WaitForExit(20000)){throw 'Backend graceful stop incomplete; providers left running.'}
Stop-Process -Id $nodeProcess.Id
if(Test-Path -LiteralPath "$privateDir/prom-process.json"){
    $prom=Get-Content -LiteralPath "$privateDir/prom-process.json" -Raw|ConvertFrom-Json
    $process=Get-Process -Id $prom.pid -ErrorAction SilentlyContinue
    if($process -and $process.ProcessName -eq 'prometheus' -and $process.StartTime.ToUniversalTime().Ticks -eq ([DateTime]$prom.started).ToUniversalTime().Ticks){Stop-Process -Id $process.Id}
}
Write-Output 'Stopped only the verified presentation demo processes. Database files preserved.'
