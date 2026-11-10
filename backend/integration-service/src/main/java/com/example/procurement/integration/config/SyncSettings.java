package com.example.procurement.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SyncSettings {
    public final int leaseSeconds;
    public final int overlapSeconds;
    public final int orderPageSize;

    public SyncSettings(@Value("${integration.sync.lease-seconds:60}") int leaseSeconds,
            @Value("${integration.sync.overlap-seconds:300}") int overlapSeconds,
            @Value("${integration.purchase-order.page-size:50}") int orderPageSize) {
        if (leaseSeconds < 45 || overlapSeconds < 0 || orderPageSize < 1 || orderPageSize > 200) {
            throw new IllegalArgumentException("采购订单同步配置超出允许范围");
        }
        this.leaseSeconds = leaseSeconds;
        this.overlapSeconds = overlapSeconds;
        this.orderPageSize = orderPageSize;
    }
}
