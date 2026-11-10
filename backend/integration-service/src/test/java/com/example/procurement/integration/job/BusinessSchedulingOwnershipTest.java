package com.example.procurement.integration.job;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import com.xxl.job.core.handler.annotation.XxlJob;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessSchedulingOwnershipTest {
    @Test
    void xxlRegistersOnlyPurchaseOrderTasks() {
        Set<String> handlers = Stream.of(XxlPurchaseOrderJobHandlers.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(XxlJob.class))
                .map(method -> method.getAnnotation(XxlJob.class).value())
                .collect(Collectors.toSet());

        assertThat(handlers).containsExactlyInAnyOrder("sapPurchaseOrderSyncJobHandler", "sapPurchaseOrderReconcileJobHandler", "sapPurchaseOrderRepairJobHandler");
    }
}
