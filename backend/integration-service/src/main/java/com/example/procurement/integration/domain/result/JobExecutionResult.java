package com.example.procurement.integration.domain.result;

import java.util.UUID;

/*
* 执行结果
* 执行完成后的统一返回:
* success(是否成功)、retryable(是否可重试)、错误码和消息。
* 配了 succeeded() / failed() 两个静态工厂方法。
* */
public record JobExecutionResult(
        UUID runId,
        boolean success,
        String errorCode,
        String message,
        boolean retryable) {

    public static JobExecutionResult succeeded(UUID runId, String message) {
        return new JobExecutionResult(runId, true, null, message, false);
    }

    public static JobExecutionResult failed(UUID runId, String code, String message, boolean retryable) {
        return new JobExecutionResult(runId, false, code, message, retryable);
    }
}
