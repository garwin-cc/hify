package com.hify.common.http;

import lombok.Getter;

/**
 * LLM HTTP 调用专用异常。
 *
 * <p>由 {@link LlmHttpClient} 抛出，调用方（domain 层）捕获后按 {@link Type}
 * 转换为对应的 {@link com.hify.common.exception.BizException}：
 * <pre>
 *   try {
 *       return llmHttpClient.post(url, headers, body);
 *   } catch (LlmApiException e) {
 *       throw switch (e.getType()) {
 *           case TIMEOUT      -> new BizException(ErrorCode.LLM_TIMEOUT, e);
 *           case AUTH_FAILED  -> new BizException(ErrorCode.LLM_PROVIDER_NOT_FOUND, e);
 *           case RATE_LIMITED -> new BizException(ErrorCode.TOO_MANY_REQUESTS, e);
 *           default           -> new BizException(ErrorCode.LLM_CALL_ERROR, e);
 *       };
 *   }
 * </pre>
 */
@Getter
public class LlmApiException extends RuntimeException {

    public enum Type {
        /** 连接超时或读超时 */
        TIMEOUT,
        /** HTTP 401 / 403，API Key 无效或权限不足 */
        AUTH_FAILED,
        /** HTTP 429，请求频率超限 */
        RATE_LIMITED,
        /** 其他网络或服务端错误 */
        UNKNOWN
    }

    private final Type type;
    /** HTTP 状态码；网络层错误（连接超时等）时为 0 */
    private final int statusCode;

    public LlmApiException(Type type, String message) {
        super(message);
        this.type = type;
        this.statusCode = 0;
    }

    public LlmApiException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.statusCode = 0;
    }

    public LlmApiException(Type type, int statusCode, String message) {
        super(message);
        this.type = type;
        this.statusCode = statusCode;
    }

    public LlmApiException(Type type, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.statusCode = statusCode;
    }
}
