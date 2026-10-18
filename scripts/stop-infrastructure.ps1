$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envFile = Join-Path $repoRoot '.env'
$composeFile = Join-Path $repoRoot 'deploy\docker-compose.yml'
if (-not (Test-Path -LiteralPath $envFile)) { throw '未找到.env，基础设施可能尚未启动。' }
docker compose --env-file $envFile -f $composeFile stop
exit $LASTEXITCODE

