package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.ExchangeRateDO;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.entity.SyncCheckpointDO;
import com.example.procurement.integration.domain.context.SyncCheckpoint;
import com.example.procurement.integration.dao.mapper.CostCenterMapper;
import com.example.procurement.integration.dao.mapper.ExchangeRateMapper;
import com.example.procurement.integration.dao.mapper.JobDefinitionMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAlertMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.dao.mapper.SyncReconciliationMapper;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.resp.CostCenterRespDTO;
import com.example.procurement.integration.dto.resp.ExchangeRateRespDTO;
import com.example.procurement.integration.dto.resp.IntegrationOverviewRespDTO;
import com.example.procurement.integration.dto.resp.JobDefinitionRespDTO;
import com.example.procurement.integration.dto.resp.JobRunDetailRespDTO;
import com.example.procurement.integration.dto.resp.JobRunRespDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.dto.resp.SyncAlertRespDTO;
import com.example.procurement.integration.dto.resp.SyncCheckpointRespDTO;
import com.example.procurement.integration.dto.resp.SyncDifferenceRespDTO;
import com.example.procurement.integration.dto.resp.SyncPageAuditRespDTO;
import com.example.procurement.integration.dto.resp.SyncPageFailureRespDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.security.IntegrationAccess;
import com.example.procurement.integration.service.IntegrationQueryService;
import com.example.procurement.integration.support.ResponseAssembler;
import com.example.procurement.integration.support.MasterDataCodec;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 页面查询与响应组装。Controller 不接触 SQL 或数据库实体。
 */
@Service
public class IntegrationQueryServiceImpl implements IntegrationQueryService {
    private final CostCenterMapper costCenters;
    private final ExchangeRateMapper exchangeRates;
    private final JobDefinitionMapper definitions;
    private final JobRunMapper runs;
    private final SyncCheckpointMapper checkpoints;
    private final SyncAuditMapper audits;
    private final SyncReconciliationMapper differences;
    private final SyncAlertMapper alerts;
    private final IntegrationAccess access;
    private final SyncSettings settings;
    private final MasterDataCodec codec;
    private final boolean mockEnabled;
    private final boolean xxlEnabled;
    private final String xxlAdminUrl;
    private final String xxlExecutorAppName;

    public IntegrationQueryServiceImpl(CostCenterMapper costCenters, ExchangeRateMapper exchangeRates,
                                       JobDefinitionMapper definitions, JobRunMapper runs, SyncCheckpointMapper checkpoints,
                                       SyncAuditMapper audits, SyncReconciliationMapper differences, SyncAlertMapper alerts,
                                       IntegrationAccess access, SyncSettings settings, MasterDataCodec codec,
                                       @Value("${integration.mock-sap.enabled:false}") boolean mockEnabled,
                                       @Value("${xxl.job.enabled:false}") boolean xxlEnabled,
                                       @Value("${xxl.job.admin-addresses:}") String xxlAdminUrl,
                                       @Value("${xxl.job.executor.app-name:procurement-integration-executor}") String xxlExecutorAppName) {
        this.costCenters = costCenters;
        this.exchangeRates = exchangeRates;
        this.definitions = definitions;
        this.runs = runs;
        this.checkpoints = checkpoints;
        this.audits = audits;
        this.differences = differences;
        this.alerts = alerts;
        this.access = access;
        this.settings = settings;
        this.mockEnabled = mockEnabled;
        this.codec = codec;
        this.xxlEnabled = xxlEnabled;
        this.xxlAdminUrl = xxlAdminUrl;
        this.xxlExecutorAppName = xxlExecutorAppName;
    }

    @Override
    public IntegrationOverviewRespDTO overview() {
        return new IntegrationOverviewRespDTO(
                costCenters.count("%%"),    // 统计已同步到SRM系统的成本中心条数 [cost_center]
                exchangeRates.count("%%"),  // 统计已同步到SRM系统的汇率条数 [exchange_rate]
                alerts.countOpen(),         // 统计SRM系统的报警条数（处理异常） [sync_alert]
                runs.countActive(null),     // 进行中的任务运行数 [job_run]
                definitions(),              // 任务定义列表 [job_definition]
                mockEnabled,                // Mock SAP 是否启用
                access.enabled(),           // 管理功能是否启用
                "XXL_JOB",                  // 调度器类型(固定 XXL_JOB)
                xxlEnabled,                 // XXL-Job 是否启用
                safeAdminUrl(xxlAdminUrl),  // XXL 管理端地址(脱敏后)
                xxlExecutorAppName,         // XXL 执行器 app 名
                false,                      // 邮件告警开关(当前未启用)
                settings.pageSize,          // 同步分页大小
                settings.writeBatchSize);   // 同步批量写入大小
    }

    @Override
    public List<JobDefinitionRespDTO> definitions() {
        return definitions.selectDefinitions();
    }

    @Override
    public PageRespDTO<CostCenterRespDTO> costCenters(int page, int size, String keyword) {
        bounds(page, size); // 校验分页范围是否合法
        String search = search(keyword);
        return new PageRespDTO<>(ResponseAssembler.list(costCenters.selectPage(search, size, offset(page, size)), CostCenterRespDTO::new),
                costCenters.count(search), page, size);
    }

    @Override
    public PageRespDTO<ExchangeRateRespDTO> exchangeRates(int page, int size, String keyword) {
        bounds(page, size);
        String search = search(keyword);
        return new PageRespDTO<>(ResponseAssembler.list(exchangeRates.selectPage(search, size, offset(page, size)), ExchangeRateRespDTO::new),
                exchangeRates.count(search), page, size);
    }

    @Override
    public ExchangeRateRespDTO resolve(String fromCurrency, String toCurrency, String rateType, LocalDate date) {
        ExchangeRateDO rate = exchangeRates.resolve(fromCurrency, toCurrency, rateType, date);
        if (rate == null) throw new IntegrationProblem("RATE_NOT_FOUND", "未找到指定业务日期可用汇率", false, 404);
        return ResponseAssembler.copy(rate, ExchangeRateRespDTO::new);
    }

    @Override
    public SyncCheckpointRespDTO checkpoint(String jobCode) {
        MasterDataType type = MasterDataType.forJob(jobCode);
        SyncCheckpointDO stored = checkpoints.selectByJob(jobCode);
        SyncCheckpoint state = stored == null || stored.getCommittedCursor() == null
                ? SyncCheckpoint.initial(type) : codec.read(stored.getCommittedCursor(), SyncCheckpoint.class);
        return new SyncCheckpointRespDTO(state.mode(), state.sourceCursor(), state.snapshotId(),
                state.snapshotPosition(), state.baselineCursor(), state.upperCursor());
    }

    @Override
    public PageRespDTO<JobRunRespDTO> runs(int page, int size, String jobCode) {
        bounds(page, size);
        String code = jobCode == null || jobCode.isBlank() ? null : jobCode;
        if (code != null) MasterDataType.forJob(code);
        return new PageRespDTO<>(ResponseAssembler.list(runs.selectPage(code, size, offset(page, size)), JobRunRespDTO::new),
                runs.countRuns(code), page, size);
    }

    @Override
    public JobRunRespDTO run(UUID runId) {
        return ResponseAssembler.copy(requireRun(runId), JobRunRespDTO::new);
    }

    @Override
    public JobRunDetailRespDTO detail(UUID runId) {
        JobRunDetailRespDTO result = ResponseAssembler.copy(requireRun(runId), JobRunDetailRespDTO::new);
        result.setPages(ResponseAssembler.list(audits.selectPages(runId), SyncPageAuditRespDTO::new));
        result.setFailures(ResponseAssembler.list(audits.selectFailures(runId), SyncPageFailureRespDTO::new));
        result.setDifferences(ResponseAssembler.list(differences.selectDifferences(runId), SyncDifferenceRespDTO::new));
        result.setDifferenceCount(differences.countDifferences(runId));
        return result;
    }

    @Override
    public PageRespDTO<SyncAlertRespDTO> alerts(int page, int size) {
        bounds(page, size);
        return new PageRespDTO<>(ResponseAssembler.list(alerts.selectPage(size, offset(page, size)), SyncAlertRespDTO::new),
                alerts.countAll(), page, size);
    }

    private JobRunDO requireRun(UUID id) {
        JobRunDO run = runs.selectById(id);
        if (run == null) throw new IntegrationProblem("NOT_FOUND", "任务不存在", false, 404);
        return run;
    }

    private void bounds(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw new IntegrationProblem("INVALID_PAGE", "分页范围不合法", false, 422);
    }

    private long offset(int page, int size) {
        return (page - 1L) * size;
    }

    private String search(String keyword) {
        return "%" + (keyword == null ? "" : keyword.substring(0, Math.min(keyword.length(), 100))) + "%";
    }

    public static String safeAdminUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) return null;
            return uri.toString();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
