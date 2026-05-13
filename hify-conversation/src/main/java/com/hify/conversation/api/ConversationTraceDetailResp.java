package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConversationTraceDetailResp {

    private String traceId;
    private String status;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime firstTokenAt;
    private LocalDateTime finishedAt;
    private AgentTrace agent;
    private ModelTrace model;
    private WorkflowTrace workflow;
    private RagTrace rag;
    private McpTrace mcp;
    private LlmTrace llm;

    @Data
    public static class AgentTrace {
        private Long id;
        private String name;
    }

    @Data
    public static class ModelTrace {
        private Long modelConfigId;
        private Long providerId;
        private String providerName;
        private String providerType;
        private String modelId;
    }

    @Data
    public static class WorkflowTrace {
        private Boolean triggered;
        private Long workflowId;
        private Long workflowRunId;
    }

    @Data
    public static class RagTrace {
        private Boolean triggered;
        private List<RagHit> hits;
    }

    @Data
    public static class RagHit {
        private Long knowledgeBaseId;
        private String knowledgeBaseName;
        private String documentId;
        private String documentName;
        private Long chunkId;
        private Integer chunkIndex;
        private Double score;
        private String contentPreview;
    }

    @Data
    public static class McpTrace {
        private Boolean triggered;
        private List<ToolCallTrace> toolCalls;
    }

    @Data
    public static class ToolCallTrace {
        private String toolName;
        private List<String> argumentKeys;
        private Long elapsedMs;
        private Boolean success;
        private String errorMessage;
    }

    @Data
    public static class LlmTrace {
        private Long providerId;
        private String providerName;
        private String providerType;
        private Long modelConfigId;
        private String modelId;
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer firstTokenLatencyMs;
        private Integer totalLatencyMs;
        private String status;
        private String errorCode;
        private String errorMessage;
    }
}
