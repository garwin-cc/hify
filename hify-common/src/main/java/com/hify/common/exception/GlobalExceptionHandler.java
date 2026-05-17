package com.hify.common.exception;

import com.hify.common.web.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        ErrorCode errorCode = e.getErrorCode();
        if ("ERROR".equals(errorCode.getLogLevel())) {
            log.error("BizException: code={} category={} retryable={} message={}",
                    e.getCode(), errorCode.getCategory(), errorCode.isRetryable(), e.getMessage(), e);
        } else {
            log.warn("BizException: code={} category={} retryable={} message={}",
                    e.getCode(), errorCode.getCategory(), errorCode.isRetryable(), e.getMessage());
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError != null
                ? fieldError.getField() + " " + fieldError.getDefaultMessage()
                : ErrorCode.PARAM_INVALID.getMessage();
        log.warn("Validation failed: {}", message);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(cv -> cv.getPropertyPath() + " " + cv.getMessage())
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        log.warn("Constraint violation: {}", message);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleAsyncRequestTimeout(AsyncRequestTimeoutException e) {
        log.warn("Async request timeout: {}", e.getMessage());
        return Result.fail(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "长连接已超时，请重新连接");
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleAsyncRequestNotUsable(AsyncRequestNotUsableException e) {
        log.debug("Async request is no longer usable: {}", e.getMessage());
        return Result.fail(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "客户端连接已断开，请重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        if (isClientAbort(e)) {
            log.debug("Client connection closed: {}", e.getMessage());
            return Result.fail(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "客户端连接已断开，请重试");
        }
        log.error("Unhandled exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private boolean isClientAbort(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String name = current.getClass().getName();
            String message = current.getMessage();
            if (name.contains("ClientAbortException")
                    || name.contains("AsyncRequestNotUsableException")
                    || (message != null && message.contains("Broken pipe"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
