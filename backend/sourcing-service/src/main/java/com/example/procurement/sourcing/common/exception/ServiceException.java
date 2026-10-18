package com.example.procurement.sourcing.common.exception;

import com.example.procurement.sourcing.common.result.IErrorCode;
import lombok.Getter;

@Getter
public class ServiceException extends AbstractException {
    public ServiceException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public ServiceException(IErrorCode errorCode) {
        super(errorCode);
    }
}
