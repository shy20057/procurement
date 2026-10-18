package com.example.procurement.integration.domain.enums;

/*
* 触发方式枚举
* */
public enum TriggerType {
    MANUAL, // 手动触发
    RETRY, // 失败后的本地重试
    XXL, // 由XXL-Job调度中心触发
    SCHEDULED, // 定时调度
    STARTUP; // 服务器重启时触发

    // 把数据库里存的字符串安全转回枚举,null 或空值兜底成 MANUAL
    public static TriggerType fromDatabase(String value) {
        return TriggerType.valueOf(value == null ? "MANUAL" : value);
    }
}
