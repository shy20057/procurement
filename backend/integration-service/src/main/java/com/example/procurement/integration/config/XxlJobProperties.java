package com.example.procurement.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/*
* 用配置创建XXL执行器
* */
@ConfigurationProperties(prefix = "xxl.job")
public record XxlJobProperties(
        boolean enabled,
        String adminAddresses,
        String accessToken,
        Executor executor) {

    public record Executor(
            String appName,
            String address,
            String ip,
            int port,
            String logPath,
            int retentionDays) {
    }
}
