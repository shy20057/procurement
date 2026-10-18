package com.example.procurement.sourcing.common.exception;

import com.example.procurement.sourcing.common.result.IErrorCode;
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
