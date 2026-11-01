package com.example.procurement.integration.controller.mock;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.dto.req.CostCenterMutationReqDTO;
import com.example.procurement.integration.dto.req.EnableReqDTO;
import com.example.procurement.integration.dto.req.ExchangeRateMutationReqDTO;
import com.example.procurement.integration.dto.resp.EnvelopeRespDTO;
import com.example.procurement.integration.dto.resp.MockSapFaultRespDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.dto.resp.SnapshotRespDTO;
import com.example.procurement.integration.dto.resp.SourcePageRespDTO;
import com.example.procurement.integration.mock.service.MockSapService;
import com.example.procurement.integration.security.IntegrationAccess;
import com.example.procurement.integration.security.MockSapAccess;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

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

    @PostMapping("/cost-centers")
    public EnvelopeRespDTO<SourceRecordDTO> costCenter(@RequestBody CostCenterMutationReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(service.save(body));
    }

    @PostMapping("/exchange-rates")
    public EnvelopeRespDTO<SourceRecordDTO> exchangeRate(@RequestBody ExchangeRateMutationReqDTO body, HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(service.save(body));
    }

    // 下面三个接口是源端给SRM同步服务调用的只读接口
    @GetMapping("/{type}/changes")
    public SourcePageRespDTO changes(@PathVariable MasterDataType type, @RequestParam String after,
                                     @RequestParam(required = false) String upper,
                                     @RequestParam(defaultValue = "500") int limit, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return service.changes(type, after, upper, limit);
    }

    @PostMapping("/{type}/snapshots")
    public SnapshotRespDTO snapshot(@PathVariable MasterDataType type, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return service.snapshot(type);
    }

    @GetMapping("/{type}/snapshots/{id}")
    public SourcePageRespDTO snapshotPage(@PathVariable MasterDataType type, @PathVariable UUID id,
                                          @RequestParam(defaultValue = "0") long after,
                                          @RequestParam(defaultValue = "500") int limit, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return service.snapshotPage(type, id, after, limit);
    }
}
