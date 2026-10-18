package com.example.procurement.integration.job;

import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.result.JobExecutionResult;
import com.example.procurement.integration.service.JobExecutionCoordinator;
import com.example.procurement.integration.support.XxlExecutionIdentity;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
* 这是真正暴露给 XXL-Job 调度中心的入口。@XxlJob("xxx") 注解的方法会被注册成
* XXL-Job 的"执行器 Handler",调度中心配置任务时填写的 Handler 名就是这里的字符串。
* */
@Component
public class XxlMasterDataJobHandlers {
    private final JobExecutionCoordinator coordinator;
    private final XxlRuntimeContext runtime;
    private final String executorAddress;

    public XxlMasterDataJobHandlers(JobExecutionCoordinator coordinator, XxlRuntimeContext runtime,
            @Value("${xxl.job.executor.address:}") String executorAddress) {
        this.coordinator = coordinator;
        this.runtime = runtime;
        this.executorAddress = executorAddress;
    }

    @XxlJob("sapCostCenterSyncJobHandler")
    public void costCenter() {
        execute("SAP_COST_CENTER_DELTA");
    }

    @XxlJob("sapExchangeRateSyncJobHandler")
    public void exchangeRate() {
        execute("SAP_EXCHANGE_RATE_DELTA");
    }

    /*
    * execute() 方法做了几件事:
    * 用 XxlExecutionIdentity.from(...) 生成幂等身份;
    * 组装 XxlTriggerMetadata;
    * 调用 coordinator.executeFromXxl(...) 真正执行业务;
    * 根据结果调用 runtime.success() / runtime.fail() 向调度中心报告。
    * */
    private void execute(String jobCode) {
        try {
            XxlExecutionIdentity identity = XxlExecutionIdentity.from(
                    jobCode, runtime.jobId(), runtime.jobLogFileName(), runtime.shardIndex());
            XxlTriggerMetadata metadata = new XxlTriggerMetadata(
                    runtime.jobId(), identity.logKey(), runtime.shardIndex(), runtime.shardTotal(), executorAddress);
            JobExecutionResult result = coordinator.executeFromXxl(jobCode, metadata, identity.requestId());
            runtime.log("SRM business runId=" + result.runId());
            if (result.success()) {
                runtime.success("SRM业务运行成功 runId=" + result.runId());
            } else {
                runtime.fail("SRM业务运行失败 runId=" + result.runId() + " code=" + result.errorCode()
                        + " message=" + safe(result.message()));
            }
        } catch (Exception failure) {
            runtime.fail("SRM任务入口失败 code=XXL_HANDLER_ERROR message=" + safe(failure.getMessage()));
        }
    }

    /*
    * safe() 方法把错误消息压缩成单行、截断到 300 字符,避免异常消息污染 XXL 日志。
    * */
    private String safe(String message) {
        if (message == null || message.isBlank()) return "未提供错误摘要";
        String singleLine = message.replace('\r', ' ').replace('\n', ' ');
        return singleLine.substring(0, Math.min(300, singleLine.length()));
    }
}
