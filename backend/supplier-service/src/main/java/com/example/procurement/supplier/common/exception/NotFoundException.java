package com.example.procurement.supplier.common.exception;

import com.example.procurement.supplier.common.result.IErrorCode;
import lombok.Getter;

@Getter
public class NotFoundException extends AbstractException {
    public NotFoundException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public NotFoundException(IErrorCode errorCode) {
        super(errorCode);
    }
}
