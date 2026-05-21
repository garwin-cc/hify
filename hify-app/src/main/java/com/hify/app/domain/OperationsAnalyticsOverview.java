package com.hify.app.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OperationsAnalyticsOverview {
    private Summary summary = new Summary();
    private List<AgentUsage> agents = new ArrayList<>();
    private List<ModelUsage> models = new ArrayList<>();
    private List<WorkflowUsage> workflows = new ArrayList<>();
    private List<McpToolUsage> mcpTools = new ArrayList<>();
    private List<ErrorUsage> errors = new ArrayList<>();
    private List<SlowLlmCall> slowLlmCalls = new ArrayList<>();
    private List<RiskConversation> riskConversations = new ArrayList<>();
    private List<DiagnosticIssue> diagnostics = new ArrayList<>();

    @Data
    public static class Summary {
        private long conversationCount;
        private long failedConversationCount;
        private double conversationFailureRate;
        private long totalTokens;
        private long ragTriggeredCount;
        private long ragHitCount;
        private double ragHitRate;
        private long workflowRunCount;
        private long workflowSuccessCount;
        private double workflowSuccessRate;
        private long mcpCallCount;
        private long mcpFailureCount;
        private double mcpFailureRate;
    }

    @Data
    public static class AgentUsage {
        private Long agentId;
        private String agentName;
        private long conversationCount;
        private long failedConversationCount;
        private double failureRate;
        private long ragTriggeredCount;
        private long mcpTriggeredCount;
        private long totalTokens;
    }

    @Data
    public static class ModelUsage {
        private Long providerId;
        private String providerType;
        private Long modelConfigId;
        private String modelId;
        private long callCount;
        private long failureCount;
        private double failureRate;
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private double avgLatencyMs;
    }

    @Data
    public static class WorkflowUsage {
        private Long workflowId;
        private String workflowName;
        private long runCount;
        private long successCount;
        private long failureCount;
        private double successRate;
        private double avgElapsedMs;
    }

    @Data
    public static class McpToolUsage {
        private Long mcpServerId;
        private String toolName;
        private long callCount;
        private long failureCount;
        private double failureRate;
        private double avgElapsedMs;
        private String lastErrorSummary;
    }

    @Data
    public static class ErrorUsage {
        private String sourceType;
        private String errorCode;
        private String errorMessage;
        private long count;
        private String lastSeenAt;
    }

    @Data
    public static class SlowLlmCall {
        private String traceId;
        private Long agentId;
        private String modelId;
        private long latencyMs;
        private long totalTokens;
        private Boolean success;
        private String errorCode;
        private String createdAt;
    }

    @Data
    public static class RiskConversation {
        private String traceId;
        private Long agentId;
        private String agentName;
        private String status;
        private Boolean ragTriggered;
        private Boolean ragHit;
        private Boolean mcpTriggered;
        private String errorCode;
        private String errorMessage;
        private String startedAt;
    }

    @Data
    public static class DiagnosticIssue {
        private String type;
        private String severity;
        private String title;
        private String description;
        private long impactCount;
        private double rate;
        private String primarySignal;
        private String recommendation;
        private String traceId;
        private String lastSeenAt;
    }
}
