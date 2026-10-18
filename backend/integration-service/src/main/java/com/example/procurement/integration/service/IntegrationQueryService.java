package com.example.procurement.integration.service;

import com.example.procurement.integration.dto.resp.CostCenterRespDTO;
import com.example.procurement.integration.dto.resp.ExchangeRateRespDTO;
import com.example.procurement.integration.dto.resp.IntegrationOverviewRespDTO;
import com.example.procurement.integration.dto.resp.JobDefinitionRespDTO;
import com.example.procurement.integration.dto.resp.JobRunDetailRespDTO;
import com.example.procurement.integration.dto.resp.JobRunRespDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.dto.resp.SyncAlertRespDTO;
import com.example.procurement.integration.dto.resp.SyncCheckpointRespDTO;

import java.util.List;
import java.util.UUID;

/** 业务能力接口：Controller、Job 入口只依赖这一层。 */
public interface IntegrationQueryService {
    IntegrationOverviewRespDTO overview();
    List<JobDefinitionRespDTO> definitions();
    PageRespDTO<CostCenterRespDTO> costCenters(int page, int size, String keyword);
    PageRespDTO<ExchangeRateRespDTO> exchangeRates(int page, int size, String keyword);
    ExchangeRateRespDTO resolve(String fromCurrency, String toCurrency, String rateType, java.time.LocalDate date);
    SyncCheckpointRespDTO checkpoint(String jobCode);
    PageRespDTO<JobRunRespDTO> runs(int page, int size, String jobCode);
    JobRunRespDTO run(UUID runId);
    JobRunDetailRespDTO detail(UUID runId);
    PageRespDTO<SyncAlertRespDTO> alerts(int page, int size);
}
