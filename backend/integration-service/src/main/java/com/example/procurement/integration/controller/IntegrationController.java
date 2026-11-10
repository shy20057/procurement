package com.example.procurement.integration.controller;

import com.example.procurement.integration.dto.req.EnableReqDTO;
import com.example.procurement.integration.dto.resp.EnvelopeRespDTO;
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

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

/**
 * HTTP 适配层：接收参数、检查管理访问权限、调用 Service、包装响应。
 */
@RestController
@RequestMapping("/api/integration")
public class IntegrationController {
    private final IntegrationQueryService queries;
    private final SyncJobService jobs;
    private final SyncAlertService alerts;
    private final IntegrationAccess access;

    public IntegrationController(IntegrationQueryService queries,
                                 SyncJobService jobs, SyncAlertService alerts, IntegrationAccess access) {
        this.queries = queries;
        this.jobs = jobs;
        this.alerts = alerts;
        this.access = access;
    }

    @GetMapping("/overview")
    public EnvelopeRespDTO<IntegrationOverviewRespDTO> overview() {
        return EnvelopeRespDTO.ok(queries.overview());
    }

    @GetMapping("/jobs")
    public EnvelopeRespDTO<List<JobDefinitionRespDTO>> definitions() {
        return EnvelopeRespDTO.ok(queries.definitions());
    }

    @PatchMapping("/jobs/{jobCode}")
    public EnvelopeRespDTO<List<JobDefinitionRespDTO>> enable(@PathVariable String jobCode, @RequestBody EnableReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        jobs.enable(jobCode, body.enabled(), access.operator(request), body.reason());
        return EnvelopeRespDTO.ok(queries.definitions());
    }

    @GetMapping("/jobs/{jobCode}/checkpoint")
    public EnvelopeRespDTO<SyncCheckpointRespDTO> checkpoint(@PathVariable String jobCode) {
        return EnvelopeRespDTO.ok(queries.checkpoint(jobCode));
    }

    @GetMapping("/job-runs")
    public EnvelopeRespDTO<PageRespDTO<JobRunRespDTO>> runs(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String jobCode) {
        return EnvelopeRespDTO.ok(queries.runs(page, size, jobCode));
    }

    @GetMapping("/job-runs/{id}")
    public EnvelopeRespDTO<JobRunDetailRespDTO> detail(@PathVariable UUID id) {
        return EnvelopeRespDTO.ok(queries.detail(id));
    }

    @GetMapping("/alerts")
    public EnvelopeRespDTO<PageRespDTO<SyncAlertRespDTO>> alerts(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String jobCode) {
        return EnvelopeRespDTO.ok(queries.alerts(page, size, jobCode));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public EnvelopeRespDTO<Void> acknowledge(@PathVariable long id, HttpServletRequest request) {
        access.requireMutation(request);
        alerts.acknowledge(id);
        return EnvelopeRespDTO.ok(null);
    }
}
