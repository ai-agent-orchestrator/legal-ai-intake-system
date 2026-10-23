param(
    [string]$PrometheusDirectory = 'C:\monitoring\prometheus',
    [string]$ActuatorUser = 'admin',
    [Parameter(Mandatory = $true)][Security.SecureString]$ActuatorPassword
)
$ErrorActionPreference = 'Stop'
$runtimePath = Join-Path $PSScriptRoot '.runtime'
$executable = Join-Path $PrometheusDirectory 'prometheus.exe'
if (!(Test-Path -LiteralPath $executable)) { throw 'Existing prometheus.exe was not found.' }
$occupied = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() |
    Where-Object Port -eq 9090
if ($occupied) { throw 'Port 9090 is already in use. Reuse/check that server before starting another.' }
if ($ActuatorUser -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Invalid Actuator username.' }
New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
$passwordPath = Join-Path $runtimePath 'actuator.password'
$plainPassword = [Net.NetworkCredential]::new('', $ActuatorPassword).Password
try { [IO.File]::WriteAllText($passwordPath, $plainPassword, [Text.UTF8Encoding]::new($false)) }
finally { $plainPassword = $null }
# Restrict the local scrape credential to this Windows user.
$acl = [Security.AccessControl.FileSecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'Allow'))
Set-Acl -LiteralPath $passwordPath -AclObject $acl
$config = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'prometheus.yml') -Raw).
    Replace('username: admin', "username: $ActuatorUser").
    Replace('password_file: .runtime/actuator.password', "password_file: '$($passwordPath.Replace('\', '/'))'")
$configPath = Join-Path $runtimePath 'prometheus.yml'
[IO.File]::WriteAllText($configPath, $config, [Text.UTF8Encoding]::new($false))
& (Join-Path $PrometheusDirectory 'promtool.exe') check config $configPath
if ($LASTEXITCODE -ne 0) { throw 'Prometheus configuration validation failed.' }
$arguments = @(
    "--config.file=`"$configPath`"",
    "--storage.tsdb.path=`"$(Join-Path $runtimePath 'data')`"",
    '--storage.tsdb.retention.time=7d',
    '--storage.tsdb.retention.size=256MB',
    '--web.listen-address=127.0.0.1:9090'
)
$process = Start-Process -FilePath $executable -ArgumentList $arguments -WorkingDirectory $runtimePath -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $runtimePath 'prometheus.stdout.log') -RedirectStandardError (Join-Path $runtimePath 'prometheus.stderr.log')
@{ processId = $process.Id; url = 'http://127.0.0.1:9090'; config = $configPath; originalDataPreserved = $true } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimePath 'process.json')
Write-Output "Reused existing Prometheus executable. PID $($process.Id), http://127.0.0.1:9090"
