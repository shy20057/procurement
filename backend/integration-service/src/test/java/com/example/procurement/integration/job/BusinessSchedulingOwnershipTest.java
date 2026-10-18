package com.example.procurement.integration.job;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessSchedulingOwnershipTest {
    @Test
    void springSchedulingContainsOnlyInternalMaintenanceWork() {
        Set<String> scheduledMethods = Stream.of(SyncJobExecutor.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertThat(scheduledMethods).containsExactlyInAnyOrder("dispatch", "heartbeats", "recovery", "staleAlerts");
        assertThat(scheduledMethods).doesNotContain("triggerCron", "weeklyReconciliation");
    }
}
