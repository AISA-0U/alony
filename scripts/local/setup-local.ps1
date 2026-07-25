. (Join-Path $PSScriptRoot 'common.ps1')

$mysql = Join-Path $script:MySqlHome 'bin\mysql.exe'
$mysqld = Join-Path $script:MySqlHome 'bin\mysqld.exe'
$opensslCandidates = @(
    'D:\Work-GIT\Git\mingw64\bin\openssl.exe',
    'D:\Work-GIT\Git\usr\bin\openssl.exe'
)
$openssl = $opensslCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

if (-not (Test-Path -LiteralPath $mysqld)) {
    throw "Install MySQL 8.0.42 under $script:MySqlHome before running setup."
}
if (-not $openssl) {
    throw 'OpenSSL was not found in the Git installation.'
}

New-Item -ItemType Directory -Force -Path $script:RuntimeRoot | Out-Null
$dataDir = Join-Path $script:RuntimeRoot 'mysql-data'
$keyDir = Join-Path $script:RuntimeRoot 'keys'
New-Item -ItemType Directory -Force -Path $keyDir | Out-Null

$freshEnvironment = -not (Test-Path -LiteralPath $script:LocalEnvPath)
if ($freshEnvironment) {
    $dbPassword = [Guid]::NewGuid().ToString('N')
    $adminPassword = 'Admin@12345'
    $studentPassword = 'Student@12345'
    $privatePem = Join-Path $keyDir 'private.pem'
    $publicPem = Join-Path $keyDir 'public.pem'
    $privateDer = Join-Path $keyDir 'private.der'
    $publicDer = Join-Path $keyDir 'public.der'

    & $openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $privatePem
    if ($LASTEXITCODE -ne 0) { throw 'Failed to generate the RSA private key.' }
    & $openssl pkey -in $privatePem -pubout -out $publicPem
    if ($LASTEXITCODE -ne 0) { throw 'Failed to generate the RSA public key.' }
    & $openssl pkcs8 -topk8 -inform PEM -outform DER -in $privatePem -nocrypt -out $privateDer
    if ($LASTEXITCODE -ne 0) { throw 'Failed to convert the RSA private key.' }
    & $openssl pkey -pubin -in $publicPem -outform DER -out $publicDer
    if ($LASTEXITCODE -ne 0) { throw 'Failed to convert the RSA public key.' }

    $privateKey = [Convert]::ToBase64String([IO.File]::ReadAllBytes($privateDer))
    $publicKey = [Convert]::ToBase64String([IO.File]::ReadAllBytes($publicDer))
    @(
        'XZS_DB_USERNAME=root'
        "XZS_DB_PASSWORD=$dbPassword"
        "XZS_RSA_PUBLIC_KEY=$publicKey"
        "XZS_RSA_PRIVATE_KEY=$privateKey"
        "XZS_ADMIN_PASSWORD=$adminPassword"
        "XZS_STUDENT_PASSWORD=$studentPassword"
    ) | Set-Content -LiteralPath $script:LocalEnvPath -Encoding ASCII
}

$envValues = Read-LocalEnvironment
$myIni = Join-Path $script:RuntimeRoot 'my.ini'
$baseDirConfig = $script:MySqlHome.Replace('\', '/')
$dataDirConfig = $dataDir.Replace('\', '/')
@"
[mysqld]
basedir=$baseDirConfig
datadir=$dataDirConfig
port=3306
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-time-zone=+08:00
log-error=$($script:RuntimeRoot.Replace('\', '/'))/mysql-error.log

[client]
port=3306
default-character-set=utf8mb4
"@ | Set-Content -LiteralPath $myIni -Encoding ASCII

$freshDatabase = -not (Test-Path -LiteralPath (Join-Path $dataDir 'mysql'))
if ($freshDatabase) {
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    & $mysqld "--defaults-file=$myIni" --initialize-insecure --console
    if ($LASTEXITCODE -ne 0) { throw 'MySQL data directory initialization failed.' }
}

Start-LocalMySql

$env:MYSQL_PWD = $envValues.XZS_DB_PASSWORD
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $mysql --protocol=TCP --host=127.0.0.1 --user=root --execute='SELECT 1;' 2>$null | Out-Null
$passwordCheckExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorAction
if ($passwordCheckExitCode -ne 0) {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null, 'Process')
    & $mysql --protocol=TCP --host=127.0.0.1 --user=root --skip-password `
        --execute="ALTER USER 'root'@'localhost' IDENTIFIED BY '$($envValues.XZS_DB_PASSWORD)';"
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL rejected both the configured password and first-run empty-password recovery.'
    }
    $env:MYSQL_PWD = $envValues.XZS_DB_PASSWORD
}

$mysqlArgs = @('--protocol=TCP', '--host=127.0.0.1', '--user=root')
$databaseExists = & $mysql @mysqlArgs --batch --skip-column-names `
    --execute="SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='xzs';"
if ($databaseExists -eq '0') {
    $baseSql = (Join-Path $script:RepoRoot 'xzs-mysql.sql').Replace('\', '/')
    & $mysql @mysqlArgs --execute="SOURCE $baseSql;"
    if ($LASTEXITCODE -ne 0) { throw 'Base database import failed.' }
}

$bankColumnExists = & $mysql @mysqlArgs --batch --skip-column-names `
    --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='xzs' AND table_name='t_question' AND column_name='bank_type';"
if ($bankColumnExists -eq '0') {
    $migrationSql = (Join-Path $script:RepoRoot 'database\migration\V4_0_0__job_question_bank.sql').Replace('\', '/')
    & $mysql @mysqlArgs --database=xzs --execute="SOURCE $migrationSql;"
    if ($LASTEXITCODE -ne 0) { throw 'Question-bank migration failed.' }
}

$passwordColumnLength = & $mysql @mysqlArgs --batch --skip-column-names `
    --execute="SELECT character_maximum_length FROM information_schema.columns WHERE table_schema='xzs' AND table_name='t_user' AND column_name='password';"
if ([int]$passwordColumnLength -lt 512) {
    & $mysql @mysqlArgs --database=xzs `
        --execute="ALTER TABLE t_user MODIFY COLUMN password varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL;"
    if ($LASTEXITCODE -ne 0) { throw 'Failed to expand the encrypted password column.' }
}

$javac = Join-Path $script:JdkHome 'bin\javac.exe'
$java = Join-Path $script:JdkHome 'bin\java.exe'
$encoderSource = Join-Path $PSScriptRoot 'LocalPasswordEncoder.java'
& $javac -d $script:RuntimeRoot $encoderSource
if ($LASTEXITCODE -ne 0) { throw 'Local password encoder compilation failed.' }
$adminEncrypted = & $java -cp $script:RuntimeRoot LocalPasswordEncoder `
    $envValues.XZS_RSA_PUBLIC_KEY $envValues.XZS_ADMIN_PASSWORD
$studentEncrypted = & $java -cp $script:RuntimeRoot LocalPasswordEncoder `
    $envValues.XZS_RSA_PUBLIC_KEY $envValues.XZS_STUDENT_PASSWORD
& $mysql @mysqlArgs --database=xzs `
    --execute="UPDATE t_user SET password='$adminEncrypted' WHERE user_name='admin'; UPDATE t_user SET password='$studentEncrypted' WHERE user_name='student';"
if ($LASTEXITCODE -ne 0) { throw 'Failed to reset local demo account passwords.' }

Write-Output 'Local environment initialized.'
Write-Output "Admin login: admin / $($envValues.XZS_ADMIN_PASSWORD)"
Write-Output "Student login: student / $($envValues.XZS_STUDENT_PASSWORD)"
