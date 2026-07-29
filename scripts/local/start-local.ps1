. (Join-Path $PSScriptRoot 'common.ps1')

$envValues = Read-LocalEnvironment
Start-LocalMySql

$env:XZS_DB_USERNAME = $envValues.XZS_DB_USERNAME
$env:XZS_DB_PASSWORD = $envValues.XZS_DB_PASSWORD
$env:XZS_RSA_PUBLIC_KEY = $envValues.XZS_RSA_PUBLIC_KEY
$env:XZS_RSA_PRIVATE_KEY = $envValues.XZS_RSA_PRIVATE_KEY
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:JAVA_HOME = $script:JdkHome
$env:Path = "$(Join-Path $script:JdkHome 'bin');$env:Path"

function Test-ExecutableBackendJar {
    param([string]$JarPath)

    if (-not (Test-Path -LiteralPath $JarPath)) {
        return $false
    }

    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
        try {
            return $null -ne $archive.GetEntry('BOOT-INF/classes/com/mindskip/xzs/XzsApplication.class')
        } finally {
            $archive.Dispose()
        }
    } catch {
        return $false
    }
}

$backendRoot = Join-Path $script:RepoRoot 'source\xzs'
$backendJar = Join-Path $backendRoot 'target\xzs-3.9.0.jar'
$needsBackendBuild = -not (Test-ExecutableBackendJar -JarPath $backendJar)
if (-not $needsBackendBuild) {
    $jarTimestamp = (Get-Item -LiteralPath $backendJar).LastWriteTimeUtc
    $newerBackendInput = Get-ChildItem (Join-Path $backendRoot 'src'), (Join-Path $backendRoot 'pom.xml') `
        -Recurse -File | Where-Object { $_.LastWriteTimeUtc -gt $jarTimestamp } | Select-Object -First 1
    $needsBackendBuild = $null -ne $newerBackendInput
}
if ($needsBackendBuild) {
    $maven = Join-Path $script:MavenHome 'bin\mvn.cmd'
    $settings = Join-Path $script:ToolchainRoot 'maven-settings.xml'
    $mavenRepo = Join-Path $script:ToolchainRoot 'm2'
    & $maven -s $settings "-Dmaven.repo.local=$mavenRepo" -DskipTests package -f (Join-Path $backendRoot 'pom.xml')
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    if (-not (Test-ExecutableBackendJar -JarPath $backendJar)) {
        throw 'Backend build did not produce an executable Spring Boot JAR.'
    }
}

Start-TrackedProcess -Name 'backend' -FilePath (Join-Path $script:JdkHome 'bin\java.exe') `
    -ArgumentList @('-jar', $backendJar) -WorkingDirectory $backendRoot

$node = Join-Path $script:NodeHome 'node.exe'
if (-not (Test-Path -LiteralPath $node)) {
    throw "Node 16 executable was not found: $node"
}
$env:Path = "$script:NodeHome;$env:Path"
$env:npm_config_cache = Join-Path $script:ToolchainRoot 'npm-cache'

$adminRoot = Join-Path $script:RepoRoot 'source\vue\xzs-admin'
$studentRoot = Join-Path $script:RepoRoot 'source\vue\xzs-student'
$adminCli = Join-Path $adminRoot 'node_modules\@vue\cli-service\bin\vue-cli-service.js'
$studentCli = Join-Path $studentRoot 'node_modules\@vue\cli-service\bin\vue-cli-service.js'
Start-TrackedProcess -Name 'admin' -FilePath $node `
    -ArgumentList @($adminCli, 'serve', '--mode', 'dev', '--no-open', '--port', '8002') -WorkingDirectory $adminRoot
Start-TrackedProcess -Name 'student' -FilePath $node `
    -ArgumentList @($studentCli, 'serve', '--mode', 'dev', '--no-open', '--port', '8001') -WorkingDirectory $studentRoot

Write-Output 'Services are starting:'
Write-Output '  Backend: http://localhost:8000'
Write-Output '  Student: http://localhost:8001'
Write-Output '  Admin:   http://localhost:8002'
Write-Output "  Admin login: admin / $($envValues.XZS_ADMIN_PASSWORD)"
Write-Output "  Student login: student / $($envValues.XZS_STUDENT_PASSWORD)"
