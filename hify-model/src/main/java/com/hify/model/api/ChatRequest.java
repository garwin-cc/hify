package com.hify.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** LLM 调用入参，供应商无关格式。由 LlmCallService 填充 modelId 后传给 Adapter。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** 调用 API 时传入的模型标识，如 gpt-4o / claude-3-7-sonnet-20250219 */
    private String modelId;

    /** 系统提示词；由 Adapter 按各供应商格式注入（OpenAI 放入 messages，Anthropic 放 system 字段） */
    private String systemPrompt;

    /** 历史消息列表（不含 system） */
    private List<ChatMessage> messages;

    /**
     * 工具定义列表，OpenAI function-calling 格式；null 表示无工具。
     * Adapter 负责按供应商格式转换（如 Anthropic 需转换字段名）。
     */
    private List<Map<String, Object>> tools;

    /** 温度参数，null 时使用模型配置默认值 */
    private BigDecimal temperature;

    /** 最大输出 token 数，null 时使用模型配置默认值 */
    private Integer maxTokens;

    /** 可选调用上下文，用于默认模型策略、fallback 和成本统计。 */
    private LlmCallContext context;
}
