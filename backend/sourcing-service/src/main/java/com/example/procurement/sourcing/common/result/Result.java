package com.example.procurement.sourcing.common.result;

import lombok.Data;
import lombok.experimental.Accessors;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {
    public static final String SUCCESS_CODE = "0";
    public static final String SUCCESS_MESSAGE = "操作成功";

    private String code;
    private String message;
    private T data;
    private String requestId;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(SUCCESS_CODE);
        result.setMessage(SUCCESS_MESSAGE);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(T data, String message) {
        Result<T> result = new Result<>();
        result.setCode(SUCCESS_CODE);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> failure(String errorCode, String errorMessage) {
        Result<T> result = new Result<>();
        result.setCode(errorCode);
        result.setMessage(errorMessage);
        return result;
    }

    public static <T> Result<T> failure(IErrorCode errorCode) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.code());
        result.setMessage(errorCode.message());
        return result;
    }
}
