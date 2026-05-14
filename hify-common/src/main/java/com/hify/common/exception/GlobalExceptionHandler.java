package com.hify.common.exception;

import com.hify.common.web.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
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

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }
}
