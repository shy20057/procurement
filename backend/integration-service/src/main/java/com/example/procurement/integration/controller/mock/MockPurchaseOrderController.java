package com.example.procurement.integration.controller.mock;

import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSnapshot;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Anchor;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Cursor;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Page;
import com.example.procurement.integration.purchase.source.MockPurchaseOrderService;
import com.example.procurement.integration.security.IntegrationAccess;
import com.example.procurement.integration.security.MockSapAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(MockPurchaseOrderController.PATH)
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockPurchaseOrderController {
    public static final String PATH = "/internal/mock-sap/purchase-orders";
    private final MockPurchaseOrderService orders;
    private final MockSapAccess sourceAccess;
    private final IntegrationAccess managementAccess;

    public MockPurchaseOrderController(MockPurchaseOrderService orders, MockSapAccess sourceAccess,
            IntegrationAccess managementAccess) {
        this.orders = orders;
        this.sourceAccess = sourceAccess;
        this.managementAccess = managementAccess;
    }

    @GetMapping("/anchor")
    public Anchor anchor(HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return orders.anchor();
    }

    @GetMapping("/baseline")
    public Page baseline(@RequestParam(defaultValue = "0") long afterId, @RequestParam long maxId,
            @RequestParam(defaultValue = "50") int limit, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return orders.baseline(afterId, maxId, limit);
    }

    @GetMapping("/changes")
    public Page changes(@RequestParam Instant startAt, @RequestParam Instant endAt,
            @RequestParam(required = false) Instant afterUpdatedAt, @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "50") int limit, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        if ((afterUpdatedAt == null) != (afterId == null)) {
            throw new IntegrationProblem("SOURCE_INVALID_CURSOR", "增量游标的时间与源订单ID必须同时提供", false, 422);
        }
        return orders.changes(startAt, endAt, afterUpdatedAt == null ? null : new Cursor(afterUpdatedAt, afterId), limit);
    }

    @GetMapping("/{sourceId}")
    public PurchaseOrderSnapshot find(@PathVariable long sourceId, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        return orders.find(sourceId).orElseThrow(() ->
                new IntegrationProblem("SOURCE_ORDER_NOT_FOUND", "源采购订单不存在", false, 404));
    }

    @PostMapping("/publish")
    public PurchaseOrderSnapshot publish(@RequestBody MockPurchaseOrderService.PublishRequest order, HttpServletRequest request) {
        sourceAccess.requireSource(request);
        managementAccess.requireMutation(request);
        return orders.publish(order);
    }
}
