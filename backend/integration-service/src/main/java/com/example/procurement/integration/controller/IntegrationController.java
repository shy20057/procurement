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

    @GetMapping("/overview")  // 【简单】加载页面基本配置
    public EnvelopeRespDTO<IntegrationOverviewRespDTO> overview() {
        return EnvelopeRespDTO.ok(queries.overview());
    }

    @GetMapping("/cost-centers") // 【简单】分页查询 「已同步到本系统的成本中心」列表,并支持按关键词搜索。它是给前端页面展示成本中心数据用的查询接口。
    public EnvelopeRespDTO<PageRespDTO<CostCenterRespDTO>> centers(@RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword) {
        return EnvelopeRespDTO.ok(queries.costCenters(page, size, keyword));
    }

    @GetMapping("/exchange-rates") // 【简单】分页查询 「已同步到本系统的汇率」列表
    public EnvelopeRespDTO<PageRespDTO<ExchangeRateRespDTO>> rates(@RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword) {
        return EnvelopeRespDTO.ok(queries.exchangeRates(page, size, keyword));
    }

    @GetMapping("/jobs") // 【简单】返回汇率&成本中心两个同步任务的数据信息
    public EnvelopeRespDTO<List<JobDefinitionRespDTO>> definitions() {
        return EnvelopeRespDTO.ok(queries.definitions());
    }

    @PatchMapping("/jobs/{jobCode}") // 【简单】打开成本中心或者汇率数据的 业务执行开关 按钮
    public EnvelopeRespDTO<List<JobDefinitionRespDTO>> enable(@PathVariable String jobCode, @RequestBody EnableReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        jobs.enable(jobCode, body.enabled());
        return EnvelopeRespDTO.ok(queries.definitions());
    }

    @GetMapping("/exchange-rates/resolve") //
    public EnvelopeRespDTO<ExchangeRateRespDTO> resolve(@RequestParam String fromCurrency, @RequestParam String toCurrency,
                                                        @RequestParam(defaultValue = "M") String rateType,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return EnvelopeRespDTO.ok(queries.resolve(fromCurrency, toCurrency, rateType, businessDate));
    }


    @GetMapping("/jobs/{jobCode}/checkpoint") // 【重要】
    public EnvelopeRespDTO<SyncCheckpointRespDTO> checkpoint(@PathVariable String jobCode) {
        return EnvelopeRespDTO.ok(queries.checkpoint(jobCode));
    }

    @PostMapping("/jobs/{jobCode}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED) //  【重要】人工触发一次主数据同步任务（提交即返回 202，实际异步执行）。
    public EnvelopeRespDTO<JobRunRespDTO> run(@PathVariable String jobCode, @RequestHeader("Idempotency-Key") String requestId,
                                              @RequestBody SyncRunReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(management.trigger(jobCode, requestId, body)); // requestId由前端生成
    }
    // requestKeys.get(action) || crypto.randomUUID() ————> 成功 → 删掉 key → 下次再点同一个按钮，会生成新 key → 后端当新请求 → 建新 run（正常语义：点一次跑一次）。
    //                                                      失败 → 保留 key → 下次再点，带同一个 key → 后端 submit 查到 job_request_key 里有这行 → 直接返回原来那条 runId，不新建、不重跑。



    @PostMapping("/jobs/{jobCode}/reset-incomplete-baseline") //
    public EnvelopeRespDTO<SyncCheckpointRespDTO> reset(@PathVariable String jobCode, @RequestBody ReasonReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        jobs.resetIncomplete(jobCode, body.reason());
        return checkpoint(jobCode);
    }


    @PostMapping("/job-runs/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED) // 【重要】工补偿：重跑一条已经失败或被取消的任务。
    public EnvelopeRespDTO<JobRunRespDTO> retry(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String requestId,
                                                @RequestBody ReasonReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(management.retry(id, requestId, body.reason()));
    }

    @PostMapping("/job-runs/{id}/cancel") // 【重要】取消数据同步任务 —— 改状态
    public EnvelopeRespDTO<JobRunRespDTO> cancel(@PathVariable UUID id, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(management.cancel(id));
    }

    @GetMapping("/job-runs") // 【简单】查询SRM系统中的同步运行记录 job_run
    public EnvelopeRespDTO<PageRespDTO<JobRunRespDTO>> runs(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String jobCode) {
        return EnvelopeRespDTO.ok(queries.runs(page, size, jobCode));
    }

    @GetMapping("/job-runs/{id}") // 【简单】查看同步运行记录的详情
    public EnvelopeRespDTO<JobRunDetailRespDTO> detail(@PathVariable UUID id) {
        return EnvelopeRespDTO.ok(queries.detail(id));
    }

    @GetMapping("/alerts") // 【简单】查询SRM系统中的告警系统 sync_alert
    public EnvelopeRespDTO<PageRespDTO<SyncAlertRespDTO>> alerts(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return EnvelopeRespDTO.ok(queries.alerts(page, size));
    }

    @PostMapping("/alerts/{id}/acknowledge") // 【简单】 把一条告警标记为「已读」—— 确认收到了，但问题没解决。
    public EnvelopeRespDTO<Void> acknowledge(@PathVariable long id, HttpServletRequest request) {
        access.requireMutation(request);
        alerts.acknowledge(id);
        return EnvelopeRespDTO.ok(null);
    }
}
