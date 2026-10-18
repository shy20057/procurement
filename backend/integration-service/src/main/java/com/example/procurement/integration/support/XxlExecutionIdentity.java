package com.example.procurement.integration.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/*
* XXL执行 执行幂等身份
* 它根据 jobCode + jobId + jobLogFileName + shardIndex 生成一个全局唯一的身份:
* logKey:对 XXL 的日志文件名做 SHA-256,得到 64 位十六进制串,作为"这次 XXL 调度"的唯一指纹;
* requestId:拼接成 xxl:jobCode:jobId:logKey:shardIndex,作为幂等键。
* 作用:XXL-Job 调度中心在任务失败后会重发同一个调度请求,通过这个身份可以识别出"这是不是同一次调度",避免重复执行。
* */
public record XxlExecutionIdentity(String logKey, String requestId) {
    public static XxlExecutionIdentity from(String jobCode, long jobId, String jobLogFileName, int shardIndex) {
        if (jobCode == null || jobCode.isBlank()) {
            throw new IllegalArgumentException("job code must not be blank");
        }
        if (jobLogFileName == null || jobLogFileName.isBlank()) {
            throw new IllegalArgumentException("XXL-JOB log file name must not be blank");
        }
        String logKey = sha256(jobLogFileName);
        return new XxlExecutionIdentity(logKey,
                "xxl:" + jobCode + ":" + jobId + ":" + logKey + ":" + shardIndex);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
