package com.example.procurement.supplier.common.exception;

import com.example.procurement.supplier.common.result.IErrorCode;
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
