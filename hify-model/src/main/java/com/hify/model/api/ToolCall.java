package com.hify.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单个工具调用请求，对应 OpenAI tool_calls[i] 格式。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 调用唯一标识，格式如 call_abc123 */
    private String id;

    /** 工具（函数）名称 */
    private String functionName;

    /** 调用参数，JSON 字符串格式 */
    private String functionArguments;
}
