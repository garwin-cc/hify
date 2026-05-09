package com.hify.common.exception;

import lombok.Getter;

/**
 * 业务异常。所有可预期的业务错误均抛此类，由 GlobalExceptionHandler 统一转换为 Result。
 *
 * <p><b>使用规则（来自 CLAUDE.md）：</b>
 * <ul>
 *   <li>只抛 BizException，不用 RuntimeException 传递业务语义</li>
 *   <li>中间层不捕获再包装，由顶层 GlobalExceptionHandler 统一处理</li>
 *   <li>包装第三方异常时，将原始异常作为 cause 传入，保留完整堆栈</li>
 * </ul>
 *
 * <p><b>典型用法：</b>
 * <pre>
 *   // 1. 仅指定错误码，使用枚举默认 message
 *   throw new BizException(ErrorCode.NOT_FOUND);
 *
 *   // 2. 覆盖 message，提供具体上下文（推荐用于 NOT_FOUND、PARAM_ERROR 等通用码）
 *   throw new BizException(ErrorCode.NOT_FOUND, "Agent ID=" + agentId + " 不存在");
 *
 *   // 3. 包装第三方异常，保留原始堆栈
 *   throw new BizException(ErrorCode.LLM_CALL_ERROR, e);
 *
 *   // 4. 包装异常 + 自定义 message
 *   throw new BizException(ErrorCode.LLM_CALL_ERROR, "OpenAI 接口返回 429", e);
 * </pre>
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    // ------------------------------------------------------------------ 构造方法

    /** 使用枚举默认 message。 */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 覆盖 message，枚举 code 不变。适用于 NOT_FOUND、PARAM_ERROR 等需要说明具体资源的场景。 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 包装底层异常，保留完整堆栈。message 使用枚举默认值。 */
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /** 包装底层异常 + 覆盖 message。调用第三方 API 失败时推荐此形式，便于日志定位。 */
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    // ------------------------------------------------------------------ 工具方法

    /** 返回错误码数字，供 GlobalExceptionHandler 填充 Result.code。 */
    public int getCode() {
        return errorCode.getCode();
    }
}
