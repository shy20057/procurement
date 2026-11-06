package com.example.procurement.integration.controller.mock;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.dto.req.CostCenterMutationReqDTO;
import com.example.procurement.integration.dto.req.EnableReqDTO;
import com.example.procurement.integration.dto.req.ExchangeRateMutationReqDTO;
import com.example.procurement.integration.dto.resp.EnvelopeRespDTO;
import com.example.procurement.integration.dto.resp.MockSapFaultRespDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.dto.resp.SourceAnchorRespDTO;
import com.example.procurement.integration.dto.domain.SourceCursorDTO;
import com.example.procurement.integration.dto.resp.SourcePageRespDTO;
import com.example.procurement.integration.mock.service.MockSapService;
import com.example.procurement.integration.security.IntegrationAccess;
import com.example.procurement.integration.security.MockSapAccess;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import com.example.procurement.integration.exception.IntegrationProblem;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integration/mock-sap")
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockSapController {
    private final MockSapService service;
    private final IntegrationAccess access;
    private final MockSapAccess sourceAccess;

    public MockSapController(MockSapService service, IntegrationAccess access, MockSapAccess sourceAccess) {
        this.service = service;
        this.access = access;
        this.sourceAccess = sourceAccess;
    }

    @GetMapping("/{type}")
    public EnvelopeRespDTO<PageRespDTO<SourceRecordDTO>> list(@PathVariable MasterDataType type, @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return EnvelopeRespDTO.ok(service.list(type, page, size));
    }

    /*  源端模拟故障并查询故障状态 */
    @GetMapping("/{type}/fault")
    public EnvelopeRespDTO<MockSapFaultRespDTO> fault(@PathVariable MasterDataType type) {
        return EnvelopeRespDTO.ok(new MockSapFaultRespDTO(service.unavailable(type)));
    }

    @PostMapping("/{type}/fault")
    public EnvelopeRespDTO<MockSapFaultRespDTO> fault(@PathVariable MasterDataType type, @RequestBody EnableReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        service.fault(type, body.enabled());
        return fault(type);
    }

    // 创建或更新成本中心
    @PostMapping("/cost-centers")
    public EnvelopeRespDTO<SourceRecordDTO> costCenter(@RequestBody CostCenterMutationReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(service.save(body));
    }

    // 创建或更新汇率
    @PostMapping("/exchange-rates")
    public EnvelopeRespDTO<SourceRecordDTO> exchangeRate(@RequestBody ExchangeRateMutationReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(service.save(body));
    }

    // 固定时间窗口，避免长尾效应
    @GetMapping("/{type}/anchor")
    public SourceAnchorRespDTO anchor(@PathVariable MasterDataType type, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return service.anchor(type);
    }

    //  按 ID 范围拉（全量基线扫描用）
    @GetMapping("/{type}/records")
    public SourcePageRespDTO records(@PathVariable MasterDataType type,
            @RequestParam(defaultValue = "0") long afterId, @RequestParam long maxId,
            @RequestParam(defaultValue = "500") int limit, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return service.fullPage(type, afterId, maxId, limit);
    }

    // 按时间窗口拉（增量同步用）   ← 最核心【重要】
    @GetMapping("/{type}/changes")
    public SourcePageRespDTO
    changes(@PathVariable MasterDataType type,
            @RequestParam Instant fromTime, @RequestParam Instant toTime,
            @RequestParam(required = false) Instant afterTime, @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "500") int limit, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        if ((afterTime == null) != (afterId == null))
            throw new IntegrationProblem("VALIDATION_ERROR", "afterTime与afterId必须同时提供", false, 422);
        return service.changes(type, fromTime, toTime,
                afterTime == null ? null : new SourceCursorDTO(afterTime, afterId), limit);
    }

    // 按 ID 单条查（对账确认用）
    @GetMapping("/{type}/records/{id}")
    public SourceRecordDTO record(@PathVariable MasterDataType type, @PathVariable long id, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return service.find(type, id);
    }
}
