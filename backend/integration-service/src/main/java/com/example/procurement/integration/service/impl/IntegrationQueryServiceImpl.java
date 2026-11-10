package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.entity.SyncCheckpointDO;
import com.example.procurement.integration.dao.mapper.JobDefinitionMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAlertMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.dao.mapper.SyncReconciliationMapper;
import com.example.procurement.integration.domain.enums.SyncJobType;
import com.example.procurement.integration.purchase.PurchaseOrderCheckpoint;
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
import com.example.procurement.integration.support.SyncJsonCodec;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 页面查询与响应组装。Controller 不接触 SQL 或数据库实体。
 */
@Service
public class IntegrationQueryServiceImpl implements IntegrationQueryService {
    private final JobDefinitionMapper definitions;
    private final JobRunMapper runs;
    private final SyncCheckpointMapper checkpoints;
    private final SyncAuditMapper audits;
    private final SyncReconciliationMapper differences;
    private final SyncAlertMapper alerts;
    private final IntegrationAccess access;
    private final SyncSettings settings;
    private final SyncJsonCodec codec;
    private final boolean mockEnabled;
    private final boolean xxlEnabled;
    private final String xxlAdminUrl;
    private final String xxlExecutorAppName;

    public IntegrationQueryServiceImpl(JobDefinitionMapper definitions, JobRunMapper runs, SyncCheckpointMapper checkpoints,
                                       SyncAuditMapper audits, SyncReconciliationMapper differences, SyncAlertMapper alerts,
                                       IntegrationAccess access, SyncSettings settings, SyncJsonCodec codec,
                                       @Value("${integration.mock-sap.enabled:false}") boolean mockEnabled,
                                       @Value("${xxl.job.enabled:false}") boolean xxlEnabled,
                                       @Value("${xxl.job.admin-addresses:}") String xxlAdminUrl,
                                       @Value("${xxl.job.executor.app-name:procurement-integration-executor}") String xxlExecutorAppName) {
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
        String code = SyncJobType.PURCHASE_ORDER.jobCode;
        return new IntegrationOverviewRespDTO(alerts.countOpen(code), runs.countActive(code), definitions(),
                mockEnabled, access.enabled(), "XXL_JOB", xxlEnabled, safeAdminUrl(xxlAdminUrl),
                xxlExecutorAppName, settings.orderPageSize, settings.overlapSeconds);
    }

    @Override
    public List<JobDefinitionRespDTO> definitions() {
        return definitions.selectDefinitions(SyncJobType.PURCHASE_ORDER.jobCode);
    }

    @Override
    public SyncCheckpointRespDTO checkpoint(String jobCode) {
        SyncJobType.forJob(jobCode);
        SyncCheckpointDO stored = checkpoints.selectByJob(jobCode);
        boolean complete = stored != null && Boolean.TRUE.equals(stored.getBaselineComplete());
        return SyncCheckpointRespDTO.purchaseOrder(complete,
                complete ? codec.read(stored.getCommittedCursor(), PurchaseOrderCheckpoint.class) : null);
    }

    @Override
    public PageRespDTO<JobRunRespDTO> runs(int page, int size, String jobCode) {
        bounds(page, size);
        String code = currentJob(jobCode);
        return new PageRespDTO<>(ResponseAssembler.list(runs.selectPage(code, size, offset(page, size)), JobRunRespDTO::new),
                runs.countRuns(code), page, size);
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
    public PageRespDTO<SyncAlertRespDTO> alerts(int page, int size, String jobCode) {
        bounds(page, size);
        String code = currentJob(jobCode);
        return new PageRespDTO<>(ResponseAssembler.list(
                alerts.selectPageByJob(code, size, offset(page, size)), SyncAlertRespDTO::new),
                alerts.countByJob(code), page, size);
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

    private String currentJob(String jobCode) {
        return jobCode == null || jobCode.isBlank() ? SyncJobType.PURCHASE_ORDER.jobCode : SyncJobType.forJob(jobCode).jobCode;
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
