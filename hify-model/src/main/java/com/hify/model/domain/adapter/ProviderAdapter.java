package com.hify.model.domain.adapter;

import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.api.ChatStreamCallback;
import com.hify.model.api.ConnectivityTestResult;
import com.hify.model.infra.ProviderPo;

import java.util.List;

public interface ProviderAdapter {

    ConnectivityTestResult testConnection(ProviderPo provider);

    List<String> listModels(ProviderPo provider);

    /**
     * 同步调用 LLM，等待完整响应后返回。
     *
     * @param provider 供应商配置（Base URL、鉴权信息）
     * @param request  调用入参，modelId 必须已填充
     * @return 完整响应
     */
    ChatResponse chat(ProviderPo provider, ChatRequest request);

    /**
     * 同步调用 LLM，允许调用方覆盖本次 HTTP 超时。
     * 未特殊实现的 Provider 使用默认 {@link #chat(ProviderPo, ChatRequest)} 行为。
     */
    default ChatResponse chat(ProviderPo provider, ChatRequest request, int timeoutSeconds) {
        return chat(provider, request);
    }

    /**
     * 流式调用 LLM，逐 token 回调，结束前阻塞当前线程。
     *
     * @param provider 供应商配置
     * @param request  调用入参
     * @param callback onToken / onComplete / onError 回调
     */
    void streamChat(ProviderPo provider, ChatRequest request, ChatStreamCallback callback);
}
