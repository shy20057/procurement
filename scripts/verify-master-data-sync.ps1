[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8096',
    [Parameter(Mandatory = $true)][string]$AdminKey,
    [switch]$IncludeRetryAlert
)

# Run only with explicit user approval. Writes are confined to the integration/mock source feature.
$ErrorActionPreference = 'Stop'
$runHeaders = @{ 'X-Integration-Admin-Key' = $AdminKey }
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8).ToUpperInvariant()
$costCode = "VERIFY_$suffix"
$rateType = "V_$suffix"

function Invoke-SyncApi([string]$Method, [string]$Path, $Body = $null, [string]$RequestKey = '') {
    $headers = @{} + $runHeaders
    if ($RequestKey) { $headers['Idempotency-Key'] = $RequestKey }
    $parameters = @{ Method = $Method; Uri = "$BaseUrl/api/integration$Path"; Headers = $headers }
    if ($null -ne $Body) {
        $parameters['ContentType'] = 'application/json; charset=utf-8'
        $parameters['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 12 -Compress))
    }
    $response = Invoke-RestMethod @parameters
    if ($response.code -ne '0') { throw "API失败：$Path $($response.code)" }
    return $response.data
}

function Assert-Sync([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    Write-Output "PASS: $Message"
}

function Start-SyncRun([string]$Job, [string]$Mode = 'AUTO') {
    return Invoke-SyncApi 'POST' "/jobs/$Job/runs" @{ mode = $Mode; reason = '用户批准的新增功能联调验证' } ([guid]::NewGuid().ToString())
}

function Wait-SyncRun([string]$Id, [int]$TimeoutSeconds = 45, [int]$MinimumAttempt = 0) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $run = Invoke-SyncApi 'GET' "/job-runs/$Id"
        if ($MinimumAttempt -gt 0 -and $run.attemptNo -ge $MinimumAttempt -and $run.status -in @('RETRY_WAIT', 'FAILED')) { return $run }
        if ($MinimumAttempt -eq 0 -and $run.status -in @('SUCCEEDED', 'FAILED', 'CANCELLED')) { return $run }
        Start-Sleep -Milliseconds 350
    }
    throw "等待运行超时：$Id"
}

$costJob = 'SAP_COST_CENTER_DELTA'
$fxJob = 'SAP_EXCHANGE_RATE_DELTA'
$originalFault = (Invoke-SyncApi 'GET' '/mock-sap/COST_CENTER/fault').unavailable
try {
    $null = Invoke-SyncApi 'POST' '/mock-sap/COST_CENTER/fault' @{ enabled = $false }
    $overview = Invoke-SyncApi 'GET' '/overview'
    Assert-Sync ($overview.jobs.Count -eq 2) '同步定义和新模块迁移可读取'

    foreach ($job in @($costJob, $fxJob)) {
        $run = Start-SyncRun $job
        $completed = Wait-SyncRun $run.runId
        Assert-Sync ($completed.status -eq 'SUCCEEDED') "$job 全量或断点同步成功"
    }

    $cc = @{ controllingArea = 'A100'; costCenterCode = $costCode; companyCode = '1000'; name = '同步验证样例'; validFrom = '2026-01-01'; validTo = '2099-12-31'; blocked = $false; deleted = $false }
    $created = Invoke-SyncApi 'POST' '/mock-sap/cost-centers' @{ data = $cc; expectedVersion = 0 }
    Assert-Sync ($created.sourceVersion -eq 1) '源端新增生成对象版本'

    $report = Start-SyncRun $costJob 'RECONCILE'
    $report = Wait-SyncRun $report.runId
    Assert-Sync ($report.status -eq 'SUCCEEDED' -and @($report.differences | Where-Object { $_.businessKey -eq "A100|$costCode" -and $_.differenceType -eq 'MISSING_TARGET' }).Count -eq 1) '只读对账识别尚未同步的源记录'

    $repair = Start-SyncRun $costJob 'REPAIR'
    $repair = Wait-SyncRun $repair.runId
    Assert-Sync ($repair.status -eq 'SUCCEEDED' -and @($repair.differences | Where-Object { $_.businessKey -eq "A100|$costCode" -and $_.repaired }).Count -eq 1) '人工对账修复补齐缺失目标记录'

    $requestKey = [guid]::NewGuid().ToString()
    $first = Invoke-SyncApi 'POST' "/jobs/$costJob/runs" @{ mode = 'AUTO'; reason = '同请求重试验证' } $requestKey
    $second = Invoke-SyncApi 'POST' "/jobs/$costJob/runs" @{ mode = 'AUTO'; reason = '同请求重试验证' } $requestKey
    Assert-Sync ($first.runId -eq $second.runId) '相同幂等键返回同一运行'
    $completed = Wait-SyncRun $first.runId
    Assert-Sync ($completed.status -eq 'SUCCEEDED') '修复后再拉增量可安全忽略相同版本'

    $cc.name = '同步验证样例-已修改'; $cc.blocked = $true
    $updated = Invoke-SyncApi 'POST' '/mock-sap/cost-centers' @{ data = $cc; expectedVersion = 1 }
    $completed = Wait-SyncRun (Start-SyncRun $costJob).runId
    $local = Invoke-SyncApi 'GET' "/cost-centers?keyword=$costCode"
    Assert-Sync ($completed.status -eq 'SUCCEEDED' -and $local.total -eq 1 -and $local.list[0].sourceVersion -eq 2 -and $local.list[0].blocked -and $local.list[0].name -eq $cc.name) '小ID旧对象修改与冻结可被增量同步'

    $rejected = $false
    try { $null = Invoke-SyncApi 'POST' '/mock-sap/cost-centers' @{ data = $cc; expectedVersion = 1 } }
    catch { $rejected = $_.Exception.Response.StatusCode.value__ -eq 409 }
    Assert-Sync $rejected '旧源版本编辑返回409'

    $cc.deleted = $true
    $null = Invoke-SyncApi 'POST' '/mock-sap/cost-centers' @{ data = $cc; expectedVersion = 2 }
    $completed = Wait-SyncRun (Start-SyncRun $costJob).runId
    $local = Invoke-SyncApi 'GET' "/cost-centers?keyword=$costCode"
    Assert-Sync ($completed.status -eq 'SUCCEEDED' -and $local.list[0].deleted -and $local.total -eq 1) '停用同步保留历史记录而不物理删除'

    $fx = @{ rateType = $rateType; fromCurrency = 'USD'; toCurrency = 'CNY'; effectiveDate = '2026-10-17'; rawRate = '7.1000'; fromFactor = '1'; toFactor = '1'; quotationMethod = 'DIRECT'; normalizedRate = $null; deleted = $false }
    $null = Invoke-SyncApi 'POST' '/mock-sap/exchange-rates' @{ data = $fx; expectedVersion = 0 }
    $null = Wait-SyncRun (Start-SyncRun $fxJob).runId
    $fx.rawRate = '7.2000'
    $null = Invoke-SyncApi 'POST' '/mock-sap/exchange-rates' @{ data = $fx; expectedVersion = 1 }
    $completed = Wait-SyncRun (Start-SyncRun $fxJob).runId
    $resolved = Invoke-SyncApi 'GET' "/exchange-rates/resolve?fromCurrency=USD&toCurrency=CNY&rateType=$rateType&businessDate=2026-10-17"
    Assert-Sync ($completed.status -eq 'SUCCEEDED' -and $resolved.sourceVersion -eq 2 -and [decimal]$resolved.normalizedRate -eq [decimal]'7.2') '同生效日汇率修订可同步并按业务日期解析'

    $null = Invoke-SyncApi 'POST' '/mock-sap/COST_CENTER/fault' @{ enabled = $true }
    $faultRun = Start-SyncRun $costJob
    $attempt = if ($IncludeRetryAlert) { 3 } else { 1 }
    $waiting = Wait-SyncRun $faultRun.runId 125 $attempt
    Assert-Sync ($waiting.status -eq 'RETRY_WAIT' -and $waiting.errorCode -eq 'SAP_HTTP_503') 'SAP故障进入持久化退避重试，不推进数据'
    if ($IncludeRetryAlert) {
        $alertPage = Invoke-SyncApi 'GET' '/alerts'
        Assert-Sync (@($alertPage.list | Where-Object { $_.jobCode -eq $costJob -and $_.category -eq 'SYNC_FAILURE' -and $_.status -eq 'OPEN' }).Count -eq 1) '连续失败触发去重告警待办'
    }
    $null = Invoke-SyncApi 'POST' "/job-runs/$($faultRun.runId)/cancel" @{}
    $null = Invoke-SyncApi 'POST' '/mock-sap/COST_CENTER/fault' @{ enabled = $false }
    $retry = Invoke-SyncApi 'POST' "/job-runs/$($faultRun.runId)/retry" @{ reason = '模拟接口恢复后人工补偿' } ([guid]::NewGuid().ToString())
    $completed = Wait-SyncRun $retry.runId
    Assert-Sync ($completed.status -eq 'SUCCEEDED' -and $completed.runId -ne $faultRun.runId) '取消等待任务后可创建关联补偿运行并恢复'

    # Restore the demo record to an available state; retain it as a visible demonstration artifact.
    $cc.deleted = $false; $cc.blocked = $false; $cc.name = '联调样例（可在源端继续修改）'
    $null = Invoke-SyncApi 'POST' '/mock-sap/cost-centers' @{ data = $cc; expectedVersion = 3 }
    $null = Wait-SyncRun (Start-SyncRun $costJob).runId
    $report = Wait-SyncRun (Start-SyncRun $costJob 'RECONCILE').runId
    Assert-Sync ($report.status -eq 'SUCCEEDED' -and $report.differenceCount -eq 0) '最终源端与本地成本中心对账无差异'
    Write-Output "验证完成；保留可观察的模拟样例：成本中心=$costCode，汇率类型=$rateType。"
} finally {
    $null = Invoke-SyncApi 'POST' '/mock-sap/COST_CENTER/fault' @{ enabled = $originalFault }
}
