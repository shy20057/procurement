[CmdletBinding()]
param(
    [string]$JavaHome,
    [switch]$SkipInfrastructure,
    [switch]$FullInfrastructure,
    [switch]$SkipFrontendBuild,
    [switch]$SkipBackendBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backendPom = Join-Path $repoRoot 'backend\pom.xml'
$composeFile = Join-Path $repoRoot 'deploy\docker-compose.yml'
$envFile = Join-Path $repoRoot '.env'
$envExample = Join-Path $repoRoot '.env.example'

function Test-Java17([string]$candidate) {
    if ([string]::IsNullOrWhiteSpace($candidate)) { return $false }
    $javaExe = Join-Path $candidate 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExe)) { return $false }
    $versionLine = (& $javaExe -version 2>&1 | Select-Object -First 1).ToString()
    return $versionLine -match 'version "17(\.|\")'
}

function Resolve-Java17([string]$requested) {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($requested) { $candidates.Add($requested) }
    if ($env:JDK17_HOME) { $candidates.Add($env:JDK17_HOME) }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    $patterns = @(
        'C:\Program Files\Eclipse Adoptium\jdk-17*',
        'C:\Program Files\Java\jdk-17*',
        'C:\Program Files\Microsoft\jdk-17*',
        'C:\Program Files\Amazon Corretto\jdk17*',
        'D:\developNew\jdk_new\jdk-17*',
        'D:\developNew\jdk*17*',
        (Join-Path $env:USERPROFILE '.jdks\*17*')
    )
    foreach ($pattern in $patterns) {
        Get-Item -Path $pattern -ErrorAction SilentlyContinue | ForEach-Object { $candidates.Add($_.FullName) }
    }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (Test-Java17 $candidate) { return [System.IO.Path]::GetFullPath($candidate) }
    }
    throw '未找到JDK 17。请使用：.\scripts\run-monolith.ps1 -JavaHome "你的JDK17目录"'
}

function Import-DotEnv([string]$path) {
    foreach ($line in Get-Content -LiteralPath $path -Encoding utf8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) { continue }
        $parts = $trimmed.Split('=', 2)
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
    }
}

$resolvedJava = Resolve-Java17 $JavaHome
$env:JAVA_HOME = $resolvedJava
$env:PATH = (Join-Path $resolvedJava 'bin') + [System.IO.Path]::PathSeparator + $env:PATH
Write-Host "Using JAVA_HOME=$resolvedJava" -ForegroundColor Green
& java -version

if (-not (Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath $envExample -Destination $envFile
    Write-Host 'Created .env from .env.example' -ForegroundColor Yellow
}
Import-DotEnv $envFile
if (-not $env:SRM_DB_URL) {
    $dbPort = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { '5433' }
    $dbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'procurement' }
    $env:SRM_DB_URL = 'jdbc:postgresql://localhost:{0}/{1}?currentSchema=srm_schema' -f $dbPort, $dbName
}

if (-not $SkipInfrastructure) {
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop未运行。请启动Docker Desktop，或使用-SkipInfrastructure连接已有中间件。' }
    $services = @('postgres', 'redis', 'rabbitmq')
    if ($FullInfrastructure) { $services = @() }
    & docker compose --env-file $envFile -f $composeFile up -d @services
    if ($LASTEXITCODE -ne 0) { throw '基础设施启动失败。' }
}

$webDir = Join-Path $repoRoot 'web'
if (-not $SkipFrontendBuild) {
    Push-Location $webDir
    try {
        if (Test-Path -LiteralPath 'package-lock.json') { & npm.cmd ci } else { & npm.cmd install }
        if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败。' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败。' }
    } finally { Pop-Location }
}

$distPath = Join-Path $webDir 'dist'
if (Test-Path -LiteralPath (Join-Path $distPath 'index.html')) {
    $distUri = ([System.Uri]([System.IO.Path]::GetFullPath($distPath) + [System.IO.Path]::DirectorySeparatorChar)).AbsoluteUri
    $env:WEB_DIST_PATH = $distUri
} else {
    Write-Host 'No frontend build found; starting backend without hosted Vue files.' -ForegroundColor Yellow
}

if (-not $SkipBackendBuild) {
    & mvn.cmd -f $backendPom -pl aggregation-service -am package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw '单体后端构建失败。' }
}

$monolithJar = Join-Path $repoRoot 'backend\aggregation-service\target\aggregation-service-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $monolithJar)) { throw '缺少单体可执行JAR，请取消-SkipBackendBuild后重试。' }

Write-Host 'Starting modular monolith at http://localhost:8090' -ForegroundColor Green
Write-Host 'Press Ctrl+C to stop the Java application. Infrastructure containers remain available for repeated tests.'
& java -jar $monolithJar
exit $LASTEXITCODE
