. (Join-Path $PSScriptRoot 'common.ps1')

$trackedServices = @(
    @{ Name = 'admin'; Path = (Join-Path $script:NodeHome 'node.exe') },
    @{ Name = 'student'; Path = (Join-Path $script:NodeHome 'node.exe') },
    @{ Name = 'backend'; Path = (Join-Path $script:JdkHome 'bin\java.exe') }
)

foreach ($service in $trackedServices) {
    $process = Get-TrackedProcess -Name $service.Name -ExpectedPath $service.Path
    if ($process) {
        Stop-Process -Id $process.Id -ErrorAction Stop
        Set-Content -LiteralPath (Join-Path $script:RuntimeRoot "$($service.Name).pid") -Value ''
        Write-Output "Stopped $($service.Name) process $($process.Id)."
    }
}

if (Test-TcpPort 3306) {
    $envValues = Read-LocalEnvironment
    $mysqlAdmin = Join-Path $script:MySqlHome 'bin\mysqladmin.exe'
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $envValues.XZS_DB_PASSWORD
        & $mysqlAdmin --protocol=tcp --host=127.0.0.1 --port=3306 "--user=$($envValues.XZS_DB_USERNAME)" shutdown
        if ($LASTEXITCODE -ne 0) {
            throw "mysqladmin shutdown failed with exit code $LASTEXITCODE."
        }
    } finally {
        $env:MYSQL_PWD = $previousPassword
    }
    Set-Content -LiteralPath (Join-Path $script:RuntimeRoot 'mysql.pid') -Value ''
    Write-Output 'Stopped MySQL.'
}
