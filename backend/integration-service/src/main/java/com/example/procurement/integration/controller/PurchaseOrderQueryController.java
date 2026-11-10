package com.example.procurement.integration.controller;

import com.example.procurement.integration.dto.resp.EnvelopeRespDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.purchase.PurchaseOrderStore;
import com.example.procurement.integration.purchase.PurchaseOrderStore.OrderSummary;
import com.example.procurement.integration.security.IntegrationAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration/purchase-orders")
public class PurchaseOrderQueryController {
    private final PurchaseOrderStore orders;
    private final IntegrationAccess access;

    public PurchaseOrderQueryController(PurchaseOrderStore orders, IntegrationAccess access) {
        this.orders = orders;
           this.access = access;
    }

    @GetMapping
    public EnvelopeRespDTO<PageRespDTO<OrderSummary>> orders(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        access.requireMutation(request);
        return EnvelopeRespDTO.ok(orders.page(page, size, keyword));
    }
}
