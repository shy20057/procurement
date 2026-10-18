package com.example.procurement.integration.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XxlExecutionIdentityTest {
    @Test
    void hashesLogFileNameAndBuildsDeterministicRequestId() {
        XxlExecutionIdentity identity = XxlExecutionIdentity.from(
                "SAP_COST_CENTER_DELTA", 18L, "/var/log/xxl/jobhandler/2026-10-18/18.log", 0);

        assertThat(identity.logKey()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(identity.logKey()).doesNotContain("/var/log");
        assertThat(identity.requestId()).isEqualTo(
                "xxl:SAP_COST_CENTER_DELTA:18:" + identity.logKey() + ":0");
        assertThat(XxlExecutionIdentity.from(
                "SAP_COST_CENTER_DELTA", 18L, "/var/log/xxl/jobhandler/2026-10-18/18.log", 0))
                .isEqualTo(identity);
    }

    @Test
    void shardIndexParticipatesInIdempotencyKey() {
        XxlExecutionIdentity first = XxlExecutionIdentity.from("SAP_COST_CENTER_DELTA", 18L, "same.log", 0);
        XxlExecutionIdentity second = XxlExecutionIdentity.from("SAP_COST_CENTER_DELTA", 18L, "same.log", 1);

        assertThat(first.logKey()).isEqualTo(second.logKey());
        assertThat(first.requestId()).isNotEqualTo(second.requestId());
    }

    @Test
    void rejectsBlankLogFileName() {
        assertThatThrownBy(() -> XxlExecutionIdentity.from("SAP_COST_CENTER_DELTA", 18L, "  ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("log file");
    }
}
