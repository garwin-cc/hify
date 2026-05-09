package com.hify.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话消息，供应商无关格式。
 *
 * <ul>
 *   <li>role=user：content 为用户输入</li>
 *   <li>role=assistant：content 为文本回复（有 toolCalls 时 content 可为 null）</li>
 *   <li>role=tool：content 为工具执行结果，toolCallId 对应触发该调用的 assistant tool_call.id</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** user / assistant / tool */
    private String role;

    /** 消息正文；role=assistant 且有工具调用时可为 null */
    private String content;

    /** 仅部分思考模型返回；工具调用续写时需原样传回供应商 */
    private String reasoningContent;

    /** 仅 role=assistant：LLM 请求发起的工具调用列表 */
    private List<ToolCall> toolCalls;

    /** 仅 role=tool：对应的 tool_call.id */
    private String toolCallId;
}
