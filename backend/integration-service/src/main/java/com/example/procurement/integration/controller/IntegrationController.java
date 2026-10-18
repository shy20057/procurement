package com.example.procurement.integration.controller;

import com.example.procurement.integration.dto.req.EnableReqDTO;
import com.example.procurement.integration.dto.req.ReasonReqDTO;
import com.example.procurement.integration.dto.req.SyncRunReqDTO;
import com.example.procurement.integration.dto.resp.CostCenterRespDTO;
import com.example.procurement.integration.dto.resp.EnvelopeRespDTO;
import com.example.procurement.integration.dto.resp.ExchangeRateRespDTO;
import com.example.procurement.integration.dto.resp.IntegrationOverviewRespDTO;
import com.example.procurement.integration.dto.resp.JobDefinitionRespDTO;
import com.example.procurement.integration.dto.resp.JobRunDetailRespDTO;
import com.example.procurement.integration.dto.resp.JobRunRespDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.dto.resp.SyncAlertRespDTO;
import com.example.procurement.integration.dto.resp.SyncCheckpointRespDTO;
import com.example.procurement.integration.security.IntegrationAccess;
import com.example.procurement.integration.service.IntegrationQueryService;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncManagementService;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP 适配层：接收参数、检查管理访问权限、调用 Service、包装响应。
 */
@RestController
@RequestMapping("/api/integration")
public class IntegrationController {
    private final IntegrationQueryService queries;
    private final SyncManagementService management;
    private final SyncJobService jobs;
    private final SyncAlertService alerts;
    private final IntegrationAccess access;

    public IntegrationController(IntegrationQueryService queries, SyncManagementService management,
                                 SyncJobService jobs, SyncAlertService alerts, IntegrationAccess access) {
        this.queries = queries;
        this.management = management;
        this.jobs = jobs;
        this.alerts = alerts;
        this.access = access;
    }

    @GetMapping("/overview")
    public EnvelopeRespDTO<IntegrationOverviewRespDTO> overview() {
        return EnvelopeRespDTO.ok(queries.overview());
    }

    @GetMapping("/cost-centers") // 分页查询 「已同步到本系统的成本中心」列表,并支持按关键词搜索。它是给前端页面展示成本中心数据用的查询接口。
    public EnvelopeRespDTO<PageRespDTO<CostCenterRespDTO>> centers(@RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword) {
        return EnvelopeRespDTO.ok(queries.costCenters(page, size, keyword));
    }

    @GetMapping("/exchange-rates") // 分页查询 「已同步到本系统的汇率」列表
    public EnvelopeRespDTO<PageRespDTO<ExchangeRateRespDTO>> rates(@RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword) {
        return EnvelopeRespDTO.ok(queries.exchangeRates(page, size, keyword));
    }

    @GetMapping("/exchange-rates/resolve")
    public EnvelopeRespDTO<ExchangeRateRespDTO> resolve(@RequestParam String fromCurrency, @RequestParam String toCurrency,
                                                        @RequestParam(defaultValue = "M") String rateType,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return EnvelopeRespDTO.ok(queries.resolve(fromCurrency, toCurrency, rateType, businessDate));
    }

    @GetMapping("/jobs")
    public EnvelopeRespDTO<List<JobDefinitionRespDTO>> definitions() {
        return EnvelopeRespDTO.ok(queries.definitions());
    }

    @GetMapping("/jobs/{jobCode}/checkpoint")
    public EnvelopeRespDTO<SyncCheckpointRespDTO> checkpoint(@PathVariable String jobCode) {
        return EnvelopeRespDTO.ok(queries.checkpoint(jobCode));
    }

    @PostMapping("/jobs/{jobCode}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EnvelopeRespDTO<JobRunRespDTO> run(@PathVariable String jobCode, @RequestHeader("Idempotency-Key") String requestId,
                                              @RequestBody SyncRunReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(management.trigger(jobCode, requestId, body));
    }

    @PatchMapping("/jobs/{jobCode}")
    public EnvelopeRespDTO<List<JobDefinitionRespDTO>> enable(@PathVariable String jobCode, @RequestBody EnableReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        jobs.enable(jobCode, body.enabled());
        return EnvelopeRespDTO.ok(queries.definitions());
    }

    @PostMapping("/jobs/{jobCode}/reset-incomplete-baseline")
    public EnvelopeRespDTO<SyncCheckpointRespDTO> reset(@PathVariable String jobCode, @RequestBody ReasonReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        jobs.resetIncomplete(jobCode, body.reason());
        return checkpoint(jobCode);
    }

    @GetMapping("/job-runs") //查询SRM系统中的同步运行记录 sync_alert
    public EnvelopeRespDTO<PageRespDTO<JobRunRespDTO>> runs(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String jobCode) {
        return EnvelopeRespDTO.ok(queries.runs(page, size, jobCode));
    }

    @GetMapping("/job-runs/{id}")
    public EnvelopeRespDTO<JobRunDetailRespDTO> detail(@PathVariable UUID id) {
        return EnvelopeRespDTO.ok(queries.detail(id));
    }

    @PostMapping("/job-runs/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EnvelopeRespDTO<JobRunRespDTO> retry(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String requestId,
                                                @RequestBody ReasonReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(management.retry(id, requestId, body.reason()));
    }

    @PostMapping("/job-runs/{id}/cancel")
    public EnvelopeRespDTO<JobRunRespDTO> cancel(@PathVariable UUID id, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(management.cancel(id));
    }

    @GetMapping("/alerts") // 查询SRM系统中的告警系统 sync_alert
    public EnvelopeRespDTO<PageRespDTO<SyncAlertRespDTO>> alerts(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return EnvelopeRespDTO.ok(queries.alerts(page, size));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public EnvelopeRespDTO<Void> acknowledge(@PathVariable long id, HttpServletRequest request) {
        access.requireMutation(request);
        alerts.acknowledge(id);
        return EnvelopeRespDTO.ok(null);
    }
}
