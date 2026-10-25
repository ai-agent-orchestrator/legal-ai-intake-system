[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$privateDir=Join-Path $projectRoot 'backend/guardrail-ready-policy-checker/.local/presentation-2026-10-25'
if(-not (Test-Path -LiteralPath "$privateDir/process.json")){throw 'Start the synthetic presentation demo first.'}
try{ $ready=Invoke-WebRequest http://127.0.0.1:9091/-/ready -UseBasicParsing -TimeoutSec 2; if($ready.StatusCode -eq 200){Write-Output 'Existing Prometheus is ready on9091. Verify targets; nothing replaced.';return} }catch{}
$config=@'
global:
  scrape_interval: 5s
scrape_configs:
  - job_name: legal-ai-intake
    metrics_path: /actuator/prometheus
    basic_auth:
      username: admin
      password: admin123
    static_configs:
      - targets: ['127.0.0.1:18080']
        labels:
          instance_name: synthetic-presentation
'@
# Public source demo account only; no real provider credential. Private config is Git-ignored.
[IO.File]::WriteAllText("$privateDir/prometheus.yml",$config,[Text.UTF8Encoding]::new($false))
& C:/monitoring/prometheus/promtool.exe check config "$privateDir/prometheus.yml"
if($LASTEXITCODE){throw 'Invalid presentation Prometheus config'}
$process=Start-Process -FilePath C:/monitoring/prometheus/prometheus.exe -ArgumentList "--config.file=$privateDir/prometheus.yml","--storage.tsdb.path=$privateDir/prom-data",'--storage.tsdb.retention.time=1d','--web.listen-address=127.0.0.1:9091' -WindowStyle Hidden -RedirectStandardOutput "$privateDir/prom.stdout.log" -RedirectStandardError "$privateDir/prom.stderr.log" -PassThru
[pscustomobject]@{pid=$process.Id;started=$process.StartTime.ToUniversalTime().ToString('o');mode='synthetic-presentation';base='http://127.0.0.1:9091'}|ConvertTo-Json|Set-Content -LiteralPath "$privateDir/prom-process.json"
Write-Output 'Presentation Prometheus started. Original TSDB/config not opened.'
