[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$AdminUrl = 'http://127.0.0.1:9090/xxl-job-admin',
    [string]$AdminKey = '',
    [ValidateSet('', 'SAP_COST_CENTER_DELTA', 'SAP_EXCHANGE_RATE_DELTA')]
    [string]$TriggerBusinessJob = '',
    [int]$WaitSeconds = 120
)

# Run only with explicit user approval. This script never starts or stops a process.
$ErrorActionPreference = 'Stop'

function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    Write-Output "PASS: $Message"
}

function Invoke-Integration([string]$Method, [string]$Path, $Body = $null, [string]$RequestKey = '') {
    $headers = @{}
    if ($AdminKey) { $headers['X-Integration-Admin-Key'] = $AdminKey }
    if ($RequestKey) { $headers['Idempotency-Key'] = $RequestKey }
    $parameters = @{ Method = $Method; Uri = "$BaseUrl/api/integration$Path"; Headers = $headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
    }
    $response = Invoke-RestMethod @parameters
    if ($response.code -ne '0') { throw "API失败：$Path code=$($response.code)" }
    return $response.data
}

$adminResponse = Invoke-WebRequest -Uri $AdminUrl -UseBasicParsing
Assert-Check ($adminResponse.StatusCode -eq 200) 'XXL-JOB Admin HTTP可访问'

$overview = Invoke-Integration 'GET' '/overview'
Assert-Check ($overview.schedulerProvider -eq 'XXL_JOB') 'SRM声明XXL-JOB为业务调度器'
Assert-Check ([bool]$overview.xxlEnabled) '当前进程已启用XXL执行器'
Assert-Check ($overview.xxlExecutorAppName -eq 'procurement-integration-executor') '执行器AppName正确'
Assert-Check ($overview.jobs.Count -eq 2) '成本中心与汇率任务定义可读取'

foreach ($job in $overview.jobs) {
    Assert-Check (-not [string]::IsNullOrWhiteSpace($job.handlerCode)) "$($job.jobCode) Handler已映射"
    Assert-Check (-not [string]::IsNullOrWhiteSpace($job.resourceKey)) "$($job.jobCode) 资源租约键已映射"
}

$recent = Invoke-Integration 'GET' '/job-runs?page=1&size=20'
$xxlRuns = @($recent.list | Where-Object { $_.triggerType -eq 'XXL' })
Write-Output "最近20条运行中XXL触发数量：$($xxlRuns.Count)"
foreach ($run in $xxlRuns) {
    Write-Output "XXL runId=$($run.runId) jobId=$($run.xxlJobId) logKey=$($run.xxlLogKey) node=$($run.executorAddress) status=$($run.status)"
}

if ($TriggerBusinessJob) {
    if (-not $AdminKey) { throw '使用-TriggerBusinessJob时必须提供-AdminKey。该参数创建SRM人工运行，不代替Admin Handler触发。' }
    $created = Invoke-Integration 'POST' "/jobs/$TriggerBusinessJob/runs" `
        @{ mode = 'AUTO'; reason = '用户明确批准的XXL功能验证脚本人工运行' } ([guid]::NewGuid().ToString())
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    do {
        Start-Sleep -Milliseconds 500
        $run = Invoke-Integration 'GET' "/job-runs/$($created.runId)"
    } while ($run.status -notin @('SUCCEEDED', 'FAILED', 'CANCELLED') -and [DateTime]::UtcNow -lt $deadline)
    Assert-Check ($run.status -in @('SUCCEEDED', 'FAILED', 'CANCELLED')) '显式人工验证运行进入终态'
    Write-Output "人工验证 runId=$($run.runId) status=$($run.status) code=$($run.errorCode)"
}
