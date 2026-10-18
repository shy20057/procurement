package com.example.procurement.sourcing.common.exception;

import com.example.procurement.sourcing.common.result.IErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends AbstractException {
    public BusinessException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public BusinessException(IErrorCode errorCode) {
        super(errorCode);
    }
}
