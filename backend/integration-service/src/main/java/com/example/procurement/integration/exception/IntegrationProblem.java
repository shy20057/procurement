package com.example.procurement.integration.exception;

public class IntegrationProblem extends RuntimeException {
    public final String code;
    public final boolean retryable;
    public final int status;

    public IntegrationProblem(String code, String message, boolean retryable, int status) {
        super(message);
        this.code = code;
        this.retryable = retryable;
        this.status = status;
    }
}
