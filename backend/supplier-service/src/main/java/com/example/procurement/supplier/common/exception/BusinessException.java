package com.example.procurement.supplier.common.exception;

import com.example.procurement.supplier.common.result.IErrorCode;
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
