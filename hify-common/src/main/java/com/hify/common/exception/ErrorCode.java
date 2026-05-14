package com.hify.common.exception;

import lombok.Getter;

/**
 * 全局错误码枚举。
 *
 * <p><b>码段约定：</b>
 * <ul>
 *   <li>4xx  — 客户端错误，与 HTTP 语义对齐，前端可直接展示 message</li>
 *   <li>5xx  — 服务端通用错误</li>
 *   <li>1xxx — LLM / 模型提供商相关</li>
 *   <li>2xxx — 知识库 / RAG 相关</li>
 *   <li>3xxx — Agent / 工作流相关</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>
 *   throw new BizException(ErrorCode.NOT_FOUND);
 *   throw new BizException(ErrorCode.NOT_FOUND, "Agent ID=" + id + " 不存在");
 * </pre>
 */
@Getter
public enum ErrorCode {

    // ================================================================
    // 4xx 客户端错误
    // ================================================================

    /** 通用参数错误，具体原因在 message 中说明 */
    PARAM_ERROR(400, "请求参数错误"),

    /** @Valid 校验失败时使用，message 由 GlobalExceptionHandler 从 BindingResult 中提取 */
    PARAM_INVALID(400, "参数校验失败"),

    /** 需要登录但未提供有效 Token */
    UNAUTHORIZED(401, "未授权，请先登录"),

    /** 已登录但无操作权限 */
    FORBIDDEN(403, "无操作权限"),

    /** 资源不存在，适用于数据库查询结果为空的场景 */
    NOT_FOUND(404, "资源不存在"),

    /** 幂等冲突，重复提交相同数据 */
    CONFLICT(409, "数据已存在，请勿重复提交"),

    /** 请求频率超限（限流） */
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试", ErrorCategory.RATE_LIMIT, true, "WARN"),

    // ================================================================
    // 5xx 服务端通用错误
    // ================================================================

    /** 兜底错误，未被明确分类的服务端异常 */
    INTERNAL_ERROR(500, "系统内部错误"),

    /** 第三方 HTTP 服务调用失败（非 LLM） */
    THIRD_PARTY_ERROR(502, "第三方服务异常"),

    /** 服务启动中、过载或维护中 */
    SERVICE_UNAVAILABLE(503, "服务暂时不可用", ErrorCategory.INFRA, true, "WARN"),

    /** 后台任务队列已满或任务被拒绝 */
    TASK_QUEUE_FULL(504, "后台任务队列已满，请稍后重试", ErrorCategory.TASK, true, "WARN"),

    // ================================================================
    // 1xxx LLM
    // ================================================================

    /** 模型提供商配置不存在或已禁用 */
    LLM_PROVIDER_NOT_FOUND(1001, "模型提供商不存在或未启用"),

    /** LLM HTTP 请求失败（网络异常、4xx/5xx 响应） */
    LLM_CALL_ERROR(1002, "LLM 调用失败", ErrorCategory.LLM, true, "WARN"),

    /** LLM 响应超时，超过 readTimeout 或 CompletableFuture 兜底超时 */
    LLM_TIMEOUT(1003, "LLM 响应超时，请稍后重试", ErrorCategory.LLM, true, "WARN"),

    /** Resilience4j 熔断器处于 OPEN 状态 */
    LLM_CIRCUIT_OPEN(1004, "LLM 服务熔断，请稍后重试", ErrorCategory.LLM, true, "WARN"),

    /** Token 数超出模型上下文窗口 */
    LLM_CONTEXT_OVERFLOW(1005, "对话上下文过长，请开启新对话"),

    // ================================================================
    // 2xxx 模型提供商 / 知识库 / RAG
    // ================================================================

    /** Provider 不存在 */
    PROVIDER_NOT_FOUND(2000, "Provider 不存在"),

    /** Provider 名称重复 */
    PROVIDER_NAME_DUPLICATE(2001, "Provider 名称已存在"),


    /** 上传文件超过大小限制 */
    KNOWLEDGE_FILE_TOO_LARGE(2101, "文件大小超过限制"),

    /** 上传文件类型不在白名单内 */
    KNOWLEDGE_FILE_TYPE_UNSUPPORTED(2102, "不支持的文件类型"),

    /** 向量化任务异步执行失败 */
    KNOWLEDGE_VECTORIZE_FAILED(2103, "文档向量化失败", ErrorCategory.RAG, true, "WARN"),

    // ================================================================
    // 3xxx Agent / 工作流
    // ================================================================

    /** Agent 引用的模型提供商已被删除或禁用 */
    AGENT_MODEL_UNAVAILABLE(3001, "Agent 绑定的模型不可用"),

    /** 工作流 JSON 配置结构不合法 */
    WORKFLOW_CONFIG_INVALID(3002, "工作流配置格式错误"),

    /** 工作流执行时某节点失败 */
    WORKFLOW_EXECUTE_FAILED(3003, "工作流执行失败", ErrorCategory.WORKFLOW, true, "WARN"),

    /** MCP 工具连接或调用失败 */
    MCP_TOOL_CALL_FAILED(3004, "MCP 工具调用失败", ErrorCategory.MCP, true, "WARN"),

    /** MCP Server 不存在或不可用 */
    MCP_SERVER_NOT_FOUND(3005, "MCP Server 不存在或未启用", ErrorCategory.MCP, false, "WARN");

    // ================================================================

    private final int code;
    private final String message;
    private final ErrorCategory category;
    private final boolean retryable;
    private final String logLevel;

    ErrorCode(int code, String message) {
        this(code, message, defaultCategory(code), false, code >= 500 ? "ERROR" : "WARN");
    }

    ErrorCode(int code, String message, ErrorCategory category, boolean retryable, String logLevel) {
        this.code = code;
        this.message = message;
        this.category = category;
        this.retryable = retryable;
        this.logLevel = logLevel;
    }

    private static ErrorCategory defaultCategory(int code) {
        if (code == 401) {
            return ErrorCategory.AUTH;
        }
        if (code == 403) {
            return ErrorCategory.PERMISSION;
        }
        if (code == 429) {
            return ErrorCategory.RATE_LIMIT;
        }
        if (code >= 1000 && code < 2000) {
            return ErrorCategory.LLM;
        }
        if (code >= 2000 && code < 3000) {
            return ErrorCategory.RAG;
        }
        if (code >= 3000 && code < 4000) {
            return ErrorCategory.WORKFLOW;
        }
        if (code >= 500) {
            return ErrorCategory.INFRA;
        }
        return ErrorCategory.VALIDATION;
    }
}
