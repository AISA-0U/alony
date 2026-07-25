$ErrorActionPreference = 'Stop'

# Some managed Windows shells expose both Path and PATH. Start-Process rejects
# that duplicate environment, so normalize it before launching child services.
$normalizedPath = $env:Path
[Environment]::SetEnvironmentVariable('PATH', $null, 'Process')
[Environment]::SetEnvironmentVariable('Path', $normalizedPath, 'Process')

$script:RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$script:WorkspaceRoot = Split-Path (Split-Path $script:RepoRoot -Parent) -Parent
$script:ToolchainRoot = Join-Path $script:WorkspaceRoot '.toolchains'
$script:RuntimeRoot = Join-Path $script:WorkspaceRoot '.runtime\xzs-exam'
$script:LocalEnvPath = Join-Path $script:RepoRoot '.env.local'
$script:MySqlHome = Join-Path $script:ToolchainRoot 'mysql\mysql-8.0.42-winx64'
$script:JdkHome = Join-Path $script:ToolchainRoot 'jdk8\jdk8u462-b08'
$script:MavenHome = Join-Path $script:ToolchainRoot 'maven\apache-maven-3.9.11'
$script:NodeHome = Join-Path $script:ToolchainRoot 'node16\node-v16.20.2-win-x64'

function Test-TcpPort {
    param([int]$Port)

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $result = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        return $result.AsyncWaitHandle.WaitOne(500) -and $client.Connected
    } finally {
        $client.Dispose()
    }
}

function Read-LocalEnvironment {
    if (-not (Test-Path -LiteralPath $script:LocalEnvPath)) {
        throw "Local environment file not found: $script:LocalEnvPath. Run setup-local.ps1 first."
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $script:LocalEnvPath) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            continue
        }
        $parts = $line.Split('=', 2)
        if ($parts.Count -eq 2) {
            $values[$parts[0]] = $parts[1]
        }
    }
    return $values
}

function Get-TrackedProcess {
    param(
        [string]$Name,
        [string]$ExpectedPath
    )

    $pidPath = Join-Path $script:RuntimeRoot "$Name.pid"
    if (-not (Test-Path -LiteralPath $pidPath)) {
        return $null
    }

    $trackedPid = Get-Content -LiteralPath $pidPath -ErrorAction SilentlyContinue
    if (-not $trackedPid) {
        return $null
    }

    $process = Get-Process -Id $trackedPid -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return $null
    }

    if ($ExpectedPath -and $process.Path -ne $ExpectedPath) {
        return $null
    }
    return $process
}

function Start-LocalMySql {
    $mysqld = Join-Path $script:MySqlHome 'bin\mysqld.exe'
    $myIni = Join-Path $script:RuntimeRoot 'my.ini'
    if (-not (Test-Path -LiteralPath $mysqld)) {
        throw "MySQL executable not found: $mysqld"
    }
    if (-not (Test-Path -LiteralPath $myIni)) {
        throw "MySQL configuration not found: $myIni. Run setup-local.ps1 first."
    }
    if (Test-TcpPort 3306) {
        return
    }

    $logDir = Join-Path $script:RuntimeRoot 'logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $process = Start-Process -FilePath $mysqld `
        -ArgumentList "--defaults-file=$myIni" `
        -WorkingDirectory $script:MySqlHome `
        -RedirectStandardOutput (Join-Path $logDir "mysql-$stamp.out.log") `
        -RedirectStandardError (Join-Path $logDir "mysql-$stamp.err.log") `
        -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath (Join-Path $script:RuntimeRoot 'mysql.pid') -Value $process.Id

    foreach ($attempt in 1..60) {
        if (Test-TcpPort 3306) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'MySQL did not open port 3306 within 30 seconds.'
}

function Start-TrackedProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory
    )

    $pidPath = Join-Path $script:RuntimeRoot "$Name.pid"
    if (Get-TrackedProcess -Name $Name -ExpectedPath $FilePath) {
        return
    }

    $logDir = Join-Path $script:RuntimeRoot 'logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $process = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput (Join-Path $logDir "$Name-$stamp.out.log") `
        -RedirectStandardError (Join-Path $logDir "$Name-$stamp.err.log") `
        -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath $pidPath -Value $process.Id
}
