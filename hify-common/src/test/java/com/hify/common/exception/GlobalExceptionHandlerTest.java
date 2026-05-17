package com.hify.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void should_returnServiceUnavailable_when_asyncRequestTimeout() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var result = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException());

        assertThat(result.getCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(result.getMessage()).isEqualTo("长连接已超时，请重新连接");
    }

    @Test
    void should_returnServiceUnavailable_when_asyncRequestNotUsable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var result = handler.handleAsyncRequestNotUsable(new AsyncRequestNotUsableException("Broken pipe"));

        assertThat(result.getCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(result.getMessage()).isEqualTo("客户端连接已断开，请重试");
    }
}
