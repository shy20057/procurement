package com.example.procurement.integration.dto.resp;

import java.util.List;

/**
 * 看板仅提供可公开的运行配置，不包含访问令牌或数据库凭证。
 */
public record IntegrationOverviewRespDTO(long costCenterCount, long exchangeRateCount,
                                         long openAlertCount, long activeRunCount, List<JobDefinitionRespDTO> jobs,
                                         boolean mockEnabled, boolean managementEnabled, String schedulerProvider,
                                         boolean xxlEnabled, String xxlAdminUrl, String xxlExecutorAppName,
                                         boolean emailEnabled, int pageSize, int writeBatchSize) {
}
