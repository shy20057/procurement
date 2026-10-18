package com.example.procurement.integration.exception;

import com.example.procurement.integration.dto.resp.EnvelopeRespDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = {"com.example.procurement.integration.controller"})
public class IntegrationExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(IntegrationExceptionHandler.class);
    @ExceptionHandler(IntegrationProblem.class)
    public ResponseEntity<?> business(IntegrationProblem problem) {
        return ResponseEntity.status(problem.status).body(new EnvelopeRespDTO<>(problem.code, problem.getMessage(), null));
    }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<?> invalid(Exception error) {
        return ResponseEntity.unprocessableEntity().body(new EnvelopeRespDTO<>("INVALID_REQUEST", "请求参数、日期、类型或必要请求头不合法", null));
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> conflict(DuplicateKeyException error) {
        return ResponseEntity.status(409).body(new EnvelopeRespDTO<>("IDEMPOTENCY_CONFLICT", "请求标识或业务键已被其他操作使用", null));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception error) {
        log.error("集成接口异常", error);
        return ResponseEntity.internalServerError().body(new EnvelopeRespDTO<>("INTEGRATION_ERROR", "集成服务暂时不可用，请检查运行环境和服务日志", null));
    }
}
