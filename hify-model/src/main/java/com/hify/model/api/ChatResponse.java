package com.hify.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** LLM 调用出参，供应商无关格式。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 文本回复内容；finish_reason=tool_calls 时为 null */
    private String content;

    /** 仅部分思考模型返回；工具调用续写时需传回 */
    private String reasoningContent;

    /** LLM 请求发起的工具调用列表；finish_reason=stop 时为 null */
    private List<ToolCall> toolCalls;

    /** 停止原因：stop / tool_calls / length / error */
    private String finishReason;

    private int inputTokens;
    private int outputTokens;

    /** 首 token 到达时间（非流式时为总耗时），毫秒 */
    private int latencyMs;
}
