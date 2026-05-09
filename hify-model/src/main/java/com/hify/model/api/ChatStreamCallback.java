package com.hify.model.api;

import com.hify.common.http.LlmApiException;

/** 流式 LLM 调用回调，由对话引擎实现后传入 Adapter。 */
public interface ChatStreamCallback {

    /** 每收到一个文本 token 时触发 */
    void onToken(String token);

    /** 流结束时触发，携带完整的响应元信息（token 数、finish_reason 等） */
    void onComplete(ChatResponse response);

    /** 发生不可恢复错误时触发；默认重新抛出 */
    default void onError(LlmApiException e) {
        throw e;
    }
}
