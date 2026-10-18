package com.example.procurement.supplier.common.exception;

import com.example.procurement.supplier.common.result.IErrorCode;
import lombok.Getter;

@Getter
public class ClientException extends AbstractException {
    public ClientException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public ClientException(IErrorCode errorCode) {
        super(errorCode);
    }
}
