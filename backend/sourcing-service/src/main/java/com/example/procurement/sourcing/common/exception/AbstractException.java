package com.example.procurement.sourcing.common.exception;

import com.example.procurement.sourcing.common.result.IErrorCode;
import lombok.Getter;

@Getter
public abstract class AbstractException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;

    public AbstractException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public AbstractException(IErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode.code();
        this.errorMessage = errorCode.message();
    }
}
