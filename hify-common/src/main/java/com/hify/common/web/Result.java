package com.hify.common.web;

import lombok.Getter;

/**
 * 统一 HTTP 响应体。
 *
 * <p>约定：code=200 表示成功，非 200 表示失败。失败时 data 为 null。
 *
 * <pre>
 * 典型用法：
 *   return Result.ok(agentDto);              // 成功，带数据
 *   return Result.ok();                      // 成功，无数据（删除、更新场景）
 *   return Result.fail("Agent 不存在");       // 失败，默认 code=500
 *   return Result.fail(400, "参数缺失");      // 失败，指定 code
 * </pre>
 *
 * @param <T> data 字段的类型
 */
@Getter
public class Result<T> {

    private int code;
    private String message;
    private T data;

    /** Jackson 反序列化 / 子类使用。业务代码通过静态工厂方法构造。 */
    protected Result() {
    }

    protected Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ------------------------------------------------------------------ 成功

    /** 成功，无 data（适用于删除、更新等操作）。 */
    public static <T> Result<T> ok() {
        return new Result<>(200, "ok", null);
    }

    /** 成功，携带 data。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "ok", data);
    }

    // ------------------------------------------------------------------ 失败

    /** 失败，code 默认 500，适合快速抛出内部错误。 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /** 失败，显式指定业务 code（对应 ErrorCode 枚举值）。 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ------------------------------------------------------------------ 工具

    public boolean isOk() {
        return this.code == 200;
    }
}
