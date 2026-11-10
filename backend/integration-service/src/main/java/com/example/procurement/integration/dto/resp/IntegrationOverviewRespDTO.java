package com.example.procurement.integration.dto.resp;

import java.util.List;

/** 同步看板的运行配置，不包含访问令牌或数据库凭证。 */
public record IntegrationOverviewRespDTO(long openAlertCount, long activeRunCount, List<JobDefinitionRespDTO> jobs,
        boolean mockEnabled, boolean managementEnabled, String schedulerProvider, boolean xxlEnabled,
        String xxlAdminUrl, String xxlExecutorAppName, int pageSize, int overlapSeconds) {}
