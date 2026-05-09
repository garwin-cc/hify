package com.hify.model.api;

/**
 * LLM 调用服务，跨模块统一入口。
 *
 * <p>调用方（对话引擎）只需传入 modelConfigId 和请求内容，
 * 本服务负责查找供应商、选择 Adapter、合并模型参数后发起调用。
 */
public interface LlmCallService {

    /**
     * 同步调用，等待完整响应后返回。
     *
     * @param modelConfigId 模型配置 ID
     * @param request       调用入参（modelId 由本服务自动填充）
     * @return 完整响应
     */
    ChatResponse chat(Long modelConfigId, ChatRequest request);

    /**
     * 流式调用，通过回调逐 token 推送。
     *
     * <p>此方法在流结束前阻塞当前线程，调用方应在专用线程池内执行。
     *
     * @param modelConfigId 模型配置 ID
     * @param request       调用入参
     * @param callback      流式回调，实现 onToken / onComplete / onError
     */
    void streamChat(Long modelConfigId, ChatRequest request, ChatStreamCallback callback);
}
