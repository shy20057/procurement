package com.example.procurement.supplier.common.global;

import com.example.procurement.supplier.common.exception.AbstractException;
import com.example.procurement.supplier.common.exception.BusinessException;
import com.example.procurement.supplier.common.exception.ClientException;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.common.exception.ServiceException;
import com.example.procurement.supplier.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice(basePackages = "com.example.procurement.supplier.controller")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value = BusinessException.class)
    public Result<Void> businessExceptionHandler(HttpServletRequest request, BusinessException ex) {
        log.warn("业务异常: {} - {}", ex.getErrorCode(), ex.getErrorMessage());
        return Result.failure(ex.getErrorCode(), ex.getErrorMessage());
    }

    @ExceptionHandler(value = NotFoundException.class)
    public Result<Void> notFoundExceptionHandler(HttpServletRequest request, NotFoundException ex) {
        log.warn("资源不存在: {} - {}", ex.getErrorCode(), ex.getErrorMessage());
        return Result.failure(ex.getErrorCode(), ex.getErrorMessage());
    }

    @ExceptionHandler(value = ClientException.class)
    public Result<Void> clientExceptionHandler(HttpServletRequest request, ClientException ex) {
        log.warn("客户端异常: {} - {}", ex.getErrorCode(), ex.getErrorMessage());
        return Result.failure(ex.getErrorCode(), ex.getErrorMessage());
    }

    @ExceptionHandler(value = ServiceException.class)
    public Result<Void> serviceExceptionHandler(HttpServletRequest request, ServiceException ex) {
        log.error("服务异常: {} - {}", ex.getErrorCode(), ex.getErrorMessage());
        return Result.failure(ex.getErrorCode(), ex.getErrorMessage());
    }

    @ExceptionHandler(value = AbstractException.class)
    public Result<Void> abstractException(HttpServletRequest request, AbstractException ex) {
        log.error("抽象异常: {} - {}", ex.getErrorCode(), ex.getErrorMessage());
        return Result.failure(ex.getErrorCode(), ex.getErrorMessage());
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getField() + ": " + fieldError.getDefaultMessage() : "请求参数错误";
        log.warn("参数校验异常: {}", message);
        return Result.failure("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(value = BindException.class)
    public Result<Void> bindExceptionHandler(HttpServletRequest request, BindException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getField() + ": " + fieldError.getDefaultMessage() : "参数绑定错误";
        log.warn("参数绑定异常: {}", message);
        return Result.failure("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public Result<Void> missingServletRequestParameterExceptionHandler(HttpServletRequest request, MissingServletRequestParameterException ex) {
        log.warn("缺少请求参数: {}", ex.getParameterName());
        return Result.failure("VALIDATION_ERROR", "缺少请求参数: " + ex.getParameterName());
    }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public Result<Void> methodArgumentTypeMismatchExceptionHandler(HttpServletRequest request, MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {} -> {}", ex.getName(), ex.getRequiredType());
        return Result.failure("VALIDATION_ERROR", "参数类型错误: " + ex.getName());
    }

    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public Result<Void> httpRequestMethodNotSupportedExceptionHandler(HttpServletRequest request, HttpRequestMethodNotSupportedException ex) {
        log.warn("不支持的请求方法: {}", ex.getMethod());
        return Result.failure("METHOD_NOT_ALLOWED", "不支持的请求方法: " + ex.getMethod());
    }

    @ExceptionHandler(value = NoHandlerFoundException.class)
    public Result<Void> noHandlerFoundExceptionHandler(HttpServletRequest request, NoHandlerFoundException ex) {
        log.warn("找不到处理器: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return Result.failure("NOT_FOUND", "接口不存在");
    }

    @ExceptionHandler(value = Throwable.class)
    public Result<Void> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("系统异常", throwable);
        return Result.failure("SYSTEM_ERROR", "系统异常，请稍后重试");
    }
}
