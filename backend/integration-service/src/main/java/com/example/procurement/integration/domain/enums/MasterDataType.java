package com.example.procurement.integration.domain.enums;

import com.example.procurement.integration.exception.IntegrationProblem;

public enum MasterDataType {
    COST_CENTER("SAP_COST_CENTER_DELTA", "cc"),
    EXCHANGE_RATE("SAP_EXCHANGE_RATE_DELTA", "fx");
    public final String jobCode;
    public final String prefix;
    MasterDataType(String jobCode, String prefix) { this.jobCode = jobCode; this.prefix = prefix; }
    public static MasterDataType forJob(String jobCode) {
        for (MasterDataType value : values()) if (value.jobCode.equals(jobCode)) return value;
        throw new IntegrationProblem("UNKNOWN_JOB", "未知同步任务", false, 404);
    }
    public String cursor(long position) { return prefix + ":" + position; }
    public long position(String cursor) {
        try {
            if (cursor == null || !cursor.startsWith(prefix + ":")) throw new IllegalArgumentException();
            long value = Long.parseLong(cursor.substring(prefix.length() + 1));
            if (value < 0) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException e) {
            throw new IntegrationProblem("INVALID_CURSOR", "游标类型或位置不合法", false, 422);
        }
    }
}
