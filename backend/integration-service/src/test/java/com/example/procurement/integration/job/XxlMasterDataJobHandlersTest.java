package com.example.procurement.integration.job;

import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.result.JobExecutionResult;
import com.example.procurement.integration.service.JobExecutionCoordinator;

import com.xxl.job.core.handler.annotation.XxlJob;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XxlMasterDataJobHandlersTest {
    @Test
    void exposesExactHandlerNamesUsedByAdminSeeds() throws Exception {
        Method costCenter = XxlMasterDataJobHandlers.class.getMethod("costCenter");
        Method exchangeRate = XxlMasterDataJobHandlers.class.getMethod("exchangeRate");

        assertThat(costCenter.getAnnotation(XxlJob.class).value()).isEqualTo("sapCostCenterSyncJobHandler");
        assertThat(exchangeRate.getAnnotation(XxlJob.class).value()).isEqualTo("sapExchangeRateSyncJobHandler");
    }

    @Test
    void reportsSuccessOnlyAfterBusinessCoordinatorSucceeds() {
        JobExecutionCoordinator coordinator = mock(JobExecutionCoordinator.class);
        FakeRuntime runtime = new FakeRuntime();
        UUID runId = UUID.randomUUID();
        when(coordinator.executeFromXxl(eq("SAP_COST_CENTER_DELTA"),
                org.mockito.ArgumentMatchers.any(XxlTriggerMetadata.class), startsWith("xxl:SAP_COST_CENTER_DELTA:")))
                .thenReturn(JobExecutionResult.succeeded(runId, "完成"));
        XxlMasterDataJobHandlers handlers = new XxlMasterDataJobHandlers(
                coordinator, runtime, "http://host.docker.internal:9999");

        handlers.costCenter();

        assertThat(runtime.successMessage).contains(runId.toString());
        assertThat(runtime.failureMessage).isNull();
        verify(coordinator).executeFromXxl(eq("SAP_COST_CENTER_DELTA"),
                org.mockito.ArgumentMatchers.any(XxlTriggerMetadata.class), startsWith("xxl:SAP_COST_CENTER_DELTA:"));
    }

    @Test
    void reportsFailureWhenBusinessRunFails() {
        JobExecutionCoordinator coordinator = mock(JobExecutionCoordinator.class);
        FakeRuntime runtime = new FakeRuntime();
        UUID runId = UUID.randomUUID();
        when(coordinator.executeFromXxl(eq("SAP_EXCHANGE_RATE_DELTA"),
                org.mockito.ArgumentMatchers.any(XxlTriggerMetadata.class), startsWith("xxl:SAP_EXCHANGE_RATE_DELTA:")))
                .thenReturn(JobExecutionResult.failed(runId, "JOB_BUSY", "资源繁忙", true));
        XxlMasterDataJobHandlers handlers = new XxlMasterDataJobHandlers(coordinator, runtime, "http://node:9999");

        handlers.exchangeRate();

        assertThat(runtime.failureMessage).contains("JOB_BUSY").contains(runId.toString());
        assertThat(runtime.successMessage).isNull();
    }

    private static final class FakeRuntime implements XxlRuntimeContext {
        private String successMessage;
        private String failureMessage;

        public long jobId() { return 42L; }
        public String jobLogFileName() { return "/tmp/xxl/42.log"; }
        public int shardIndex() { return 0; }
        public int shardTotal() { return 1; }
        public void log(String message) { }
        public void success(String message) { successMessage = message; }
        public void fail(String message) { failureMessage = message; }
    }
}
