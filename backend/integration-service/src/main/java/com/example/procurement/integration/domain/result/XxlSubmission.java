package com.example.procurement.integration.domain.result;

import java.util.UUID;

/*
* XXL提交结果
* submitXxl 把任务落库后返回的结果,告诉调用方这次提交是什么状态:
* */
public record XxlSubmission(
        UUID runId,
        State state,
        String terminalStatus,
        String errorCode,
        String errorSummary) {

    public enum State { CREATED, DUPLICATE_TERMINAL, BUSY, DISABLED }

    /*
    * 正常创建，可以继续执行
    * */
    public static XxlSubmission created(UUID runId) {
        return new XxlSubmission(runId, State.CREATED, null, null, null);
    }

    /*
    * 幂等识别出这是重复调度,且原任务已终结(成功/失败)
    * */
    public static XxlSubmission terminal(UUID runId, String status, String errorCode, String errorSummary) {
        return new XxlSubmission(runId, State.DUPLICATE_TERMINAL, status, errorCode, errorSummary);
    }

    /*
    * 资源正被占用
    * */
    public static XxlSubmission busy(UUID runId, String summary) {
        return new XxlSubmission(runId, State.BUSY, "FAILED", "JOB_BUSY", summary);
    }

    /*
    * 任务被业务开关停用
    * */
    public static XxlSubmission disabled(UUID runId) {
        return new XxlSubmission(runId, State.DISABLED, "FAILED", "JOB_DISABLED", "当前任务已被业务执行开关停用");
    }
}
