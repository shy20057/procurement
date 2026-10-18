package com.example.procurement.integration.dto.resp;
public record EnvelopeRespDTO<T>(String code, String message, T data) {
    public static <T> EnvelopeRespDTO<T> ok(T data) {
        return new EnvelopeRespDTO<>("0", "操作成功", data);
    }
}
