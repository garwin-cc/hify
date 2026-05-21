package com.hify.app.domain;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperationsAnalyticsServiceImpl implements OperationsAnalyticsService {

    private static final int LIMIT = 10;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OperationsAnalyticsServiceImpl(@Qualifier("mysqlNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OperationsAnalyticsOverview overview(Long projectId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to != null ? to : LocalDateTime.now();
        LocalDateTime start = from != null ? from : end.minusDays(7);
        if (start.isAfter(end)) {
            start = end.minusDays(7);
        }

        MapSqlParameterSource params = params(projectId, start, end);
        OperationsAnalyticsOverview overview = new OperationsAnalyticsOverview();
        fillSummary(overview.getSummary(), params);
        overview.setAgents(loadAgents(params));
        overview.setModels(loadModels(params));
        overview.setWorkflows(loadWorkflows(params));
        overview.setMcpTools(loadMcpTools(params));
        overview.setErrors(loadErrors(params));
        overview.setSlowLlmCalls(loadSlowLlmCalls(params));
        overview.setRiskConversations(loadRiskConversations(params));
        overview.setDiagnostics(buildDiagnostics(overview));
        return overview;
    }

    private List<OperationsAnalyticsOverview.DiagnosticIssue> buildDiagnostics(OperationsAnalyticsOverview overview) {
        List<OperationsAnalyticsOverview.DiagnosticIssue> issues = new ArrayList<>();
        OperationsAnalyticsOverview.Summary summary = overview.getSummary();

        if (summary.getFailedConversationCount() > 0) {
            OperationsAnalyticsOverview.ErrorUsage topError = first(overview.getErrors());
            issues.add(issue(
                    "CONVERSATION_FAILURE",
                    severity(summary.getConversationFailureRate()),
                    "对话失败率偏高",
                    "时间范围内存在失败对话，用户会直接看到系统错误或中断响应。",
                    summary.getFailedConversationCount(),
                    summary.getConversationFailureRate(),
                    topError == null ? "失败对话 " + summary.getFailedConversationCount() + " 次" : topError.getErrorCode(),
                    "优先打开日志中心按 traceId 查看失败对话，确认是 LLM、RAG、MCP 还是后端异常导致。",
                    firstTrace(overview.getRiskConversations()),
                    topError == null ? null : topError.getLastSeenAt()
            ));
        }

        long ragMissCount = Math.max(0, summary.getRagTriggeredCount() - summary.getRagHitCount());
        if (ragMissCount > 0) {
            double missRate = rate(ragMissCount, summary.getRagTriggeredCount());
            issues.add(issue(
                    "RAG_MISS",
                    severity(missRate),
                    "RAG 触发但未命中",
                    "Agent 已触发知识库检索，但部分对话没有召回可用片段，回答可能退化为纯模型能力。",
                    ragMissCount,
                    missRate,
                    ragMissCount + "/" + summary.getRagTriggeredCount() + " 次未命中",
                    "检查知识库绑定、检索阈值、embedding 模型维度和文档分块状态，必要时重建索引或调低阈值。",
                    firstRagMissTrace(overview.getRiskConversations()),
                    null
            ));
        }

        long workflowFailureCount = Math.max(0, summary.getWorkflowRunCount() - summary.getWorkflowSuccessCount());
        if (workflowFailureCount > 0) {
            double failureRate = rate(workflowFailureCount, summary.getWorkflowRunCount());
            OperationsAnalyticsOverview.WorkflowUsage workflow = first(overview.getWorkflows());
            issues.add(issue(
                    "WORKFLOW_FAILURE",
                    severity(failureRate),
                    "Workflow 终态失败",
                    "工作流存在失败、超时或取消运行，绑定该工作流的 Agent 会出现不可预期输出。",
                    workflowFailureCount,
                    failureRate,
                    workflow == null ? "失败运行 " + workflowFailureCount + " 次" : workflow.getWorkflowName(),
                    "进入工作流运行详情查看失败节点、节点输入输出和外部调用耗时，先处理失败次数最高的工作流。",
                    null,
                    null
            ));
        }

        if (summary.getMcpFailureCount() > 0) {
            OperationsAnalyticsOverview.McpToolUsage tool = first(overview.getMcpTools());
            issues.add(issue(
                    "MCP_FAILURE",
                    severity(summary.getMcpFailureRate()),
                    "MCP 工具调用失败",
                    "工具调用失败会让 Agent 的动作能力降级，模型可能只能返回加工后的错误信息。",
                    summary.getMcpFailureCount(),
                    summary.getMcpFailureRate(),
                    tool == null ? "失败调用 " + summary.getMcpFailureCount() + " 次" : tool.getToolName(),
                    "检查 MCP 服务可达性、工具参数 schema、超时设置和最近错误摘要，必要时先禁用高风险工具。",
                    null,
                    null
            ));
        }

        OperationsAnalyticsOverview.SlowLlmCall slowLlmCall = first(overview.getSlowLlmCalls());
        if (slowLlmCall != null && slowLlmCall.getLatencyMs() >= 2000) {
            issues.add(issue(
                    "SLOW_LLM",
                    slowLlmCall.getLatencyMs() >= 10000 ? "HIGH" : "MEDIUM",
                    "LLM 调用耗时偏高",
                    "慢模型调用会拖慢 SSE 首包和整体响应，排队时还会放大并发压力。",
                    1,
                    0.0,
                    slowLlmCall.getModelId() + " · " + slowLlmCall.getLatencyMs() + "ms",
                    "检查模型供应商延迟、输入 token、上下文轮数和 RAG 注入长度，必要时切换模型或降低上下文规模。",
                    slowLlmCall.getTraceId(),
                    slowLlmCall.getCreatedAt()
            ));
        }

        issues.sort(Comparator
                .comparingInt((OperationsAnalyticsOverview.DiagnosticIssue issue) -> severityRank(issue.getSeverity()))
                .thenComparing(OperationsAnalyticsOverview.DiagnosticIssue::getImpactCount, Comparator.reverseOrder())
                .thenComparing(OperationsAnalyticsOverview.DiagnosticIssue::getType));
        return issues;
    }

    private void fillSummary(OperationsAnalyticsOverview.Summary summary, MapSqlParameterSource params) {
        Map<String, Object> conversation = one("""
                SELECT COUNT(*) conversation_count,
                       SUM(CASE WHEN status IN ('ERROR','TIMEOUT','BACKEND_ERROR','FAILED') THEN 1 ELSE 0 END) failed_count,
                       SUM(CASE WHEN rag_triggered = 1 THEN 1 ELSE 0 END) rag_triggered_count
                  FROM t_conversation_trace
                 WHERE deleted = 0
                   AND started_at >= :from AND started_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                """, params);
        long conversationCount = lng(conversation.get("conversation_count"));
        long failedConversationCount = lng(conversation.get("failed_count"));
        long ragTriggeredCount = lng(conversation.get("rag_triggered_count"));
        long ragHitCount = count("""
                SELECT COUNT(DISTINCT c.trace_id)
                  FROM t_conversation_trace c
                  JOIN t_conversation_rag_trace r ON r.trace_id = c.trace_id AND r.deleted = 0
                 WHERE c.deleted = 0
                   AND c.rag_triggered = 1
                   AND c.started_at >= :from AND c.started_at < :to
                   AND (:projectId IS NULL OR c.project_id = :projectId)
                """, params);

        long totalTokens = count("""
                SELECT COALESCE(SUM(input_tokens + output_tokens), 0)
                  FROM t_llm_call_stat
                 WHERE deleted = 0
                   AND created_at >= :from AND created_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                """, params);

        Map<String, Object> workflow = one("""
                SELECT COUNT(*) run_count,
                       SUM(CASE WHEN wr.status = 'SUCCESS' THEN 1 ELSE 0 END) success_count
                  FROM t_workflow_run wr
                  JOIN t_workflow w ON w.id = wr.workflow_id AND w.deleted = 0
                 WHERE wr.deleted = 0
                   AND wr.status IN ('SUCCESS','FAILED','TIMEOUT','CANCELED')
                   AND wr.created_at >= :from AND wr.created_at < :to
                   AND (:projectId IS NULL OR w.project_id = :projectId)
                """, params);

        Map<String, Object> mcp = one("""
                SELECT COUNT(*) call_count,
                       SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) failure_count
                  FROM t_mcp_tool_call_audit
                 WHERE deleted = 0
                   AND created_at >= :from AND created_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                """, params);

        summary.setConversationCount(conversationCount);
        summary.setFailedConversationCount(failedConversationCount);
        summary.setConversationFailureRate(rate(failedConversationCount, conversationCount));
        summary.setTotalTokens(totalTokens);
        summary.setRagTriggeredCount(ragTriggeredCount);
        summary.setRagHitCount(ragHitCount);
        summary.setRagHitRate(rate(ragHitCount, ragTriggeredCount));
        summary.setWorkflowRunCount(lng(workflow.get("run_count")));
        summary.setWorkflowSuccessCount(lng(workflow.get("success_count")));
        summary.setWorkflowSuccessRate(rate(summary.getWorkflowSuccessCount(), summary.getWorkflowRunCount()));
        summary.setMcpCallCount(lng(mcp.get("call_count")));
        summary.setMcpFailureCount(lng(mcp.get("failure_count")));
        summary.setMcpFailureRate(rate(summary.getMcpFailureCount(), summary.getMcpCallCount()));
    }

    private List<OperationsAnalyticsOverview.AgentUsage> loadAgents(MapSqlParameterSource params) {
        Map<Long, Long> tokensByAgent = new HashMap<>();
        jdbcTemplate.query("""
                SELECT agent_id, COALESCE(SUM(input_tokens + output_tokens), 0) total_tokens
                  FROM t_llm_call_stat
                 WHERE deleted = 0
                   AND agent_id IS NOT NULL
                   AND created_at >= :from AND created_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                 GROUP BY agent_id
                """, params, rs -> {
            tokensByAgent.put(rs.getLong("agent_id"), rs.getLong("total_tokens"));
        });

        return jdbcTemplate.query("""
                SELECT agent_id,
                       COALESCE(MAX(agent_name), CONCAT('Agent ', agent_id)) agent_name,
                       COUNT(*) conversation_count,
                       SUM(CASE WHEN status IN ('ERROR','TIMEOUT','BACKEND_ERROR','FAILED') THEN 1 ELSE 0 END) failed_count,
                       SUM(CASE WHEN rag_triggered = 1 THEN 1 ELSE 0 END) rag_count,
                       SUM(CASE WHEN mcp_triggered = 1 THEN 1 ELSE 0 END) mcp_count
                  FROM t_conversation_trace
                 WHERE deleted = 0
                   AND started_at >= :from AND started_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                 GROUP BY agent_id
                 ORDER BY conversation_count DESC, agent_id ASC
                 LIMIT %d
                """.formatted(LIMIT), params, (rs, rowNum) -> {
            OperationsAnalyticsOverview.AgentUsage item = new OperationsAnalyticsOverview.AgentUsage();
            long count = rs.getLong("conversation_count");
            long failed = rs.getLong("failed_count");
            Long agentId = rs.getLong("agent_id");
            item.setAgentId(agentId);
            item.setAgentName(rs.getString("agent_name"));
            item.setConversationCount(count);
            item.setFailedConversationCount(failed);
            item.setFailureRate(rate(failed, count));
            item.setRagTriggeredCount(rs.getLong("rag_count"));
            item.setMcpTriggeredCount(rs.getLong("mcp_count"));
            item.setTotalTokens(tokensByAgent.getOrDefault(agentId, 0L));
            return item;
        });
    }

    private List<OperationsAnalyticsOverview.ModelUsage> loadModels(MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT provider_id, provider_type, model_config_id, model_id,
                       COUNT(*) call_count,
                       SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) failure_count,
                       COALESCE(SUM(input_tokens), 0) input_tokens,
                       COALESCE(SUM(output_tokens), 0) output_tokens,
                       COALESCE(AVG(latency_ms), 0) avg_latency_ms
                  FROM t_llm_call_stat
                 WHERE deleted = 0
                   AND created_at >= :from AND created_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                 GROUP BY provider_id, provider_type, model_config_id, model_id
                 ORDER BY (COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0)) DESC
                 LIMIT %d
                """.formatted(LIMIT), params, (rs, rowNum) -> {
            OperationsAnalyticsOverview.ModelUsage item = new OperationsAnalyticsOverview.ModelUsage();
            long callCount = rs.getLong("call_count");
            long failureCount = rs.getLong("failure_count");
            long inputTokens = rs.getLong("input_tokens");
            long outputTokens = rs.getLong("output_tokens");
            item.setProviderId(rs.getLong("provider_id"));
            item.setProviderType(rs.getString("provider_type"));
            item.setModelConfigId(rs.getLong("model_config_id"));
            item.setModelId(rs.getString("model_id"));
            item.setCallCount(callCount);
            item.setFailureCount(failureCount);
            item.setFailureRate(rate(failureCount, callCount));
            item.setInputTokens(inputTokens);
            item.setOutputTokens(outputTokens);
            item.setTotalTokens(inputTokens + outputTokens);
            item.setAvgLatencyMs(round2(rs.getDouble("avg_latency_ms")));
            return item;
        });
    }

    private List<OperationsAnalyticsOverview.WorkflowUsage> loadWorkflows(MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT wr.workflow_id,
                       COALESCE(MAX(w.name), CONCAT('Workflow ', wr.workflow_id)) workflow_name,
                       COUNT(*) run_count,
                       SUM(CASE WHEN wr.status = 'SUCCESS' THEN 1 ELSE 0 END) success_count,
                       SUM(CASE WHEN wr.status IN ('FAILED','TIMEOUT','CANCELED') THEN 1 ELSE 0 END) failure_count,
                       COALESCE(AVG(wr.elapsed_ms), 0) avg_elapsed_ms
                  FROM t_workflow_run wr
                  JOIN t_workflow w ON w.id = wr.workflow_id AND w.deleted = 0
                 WHERE wr.deleted = 0
                   AND wr.status IN ('SUCCESS','FAILED','TIMEOUT','CANCELED')
                   AND wr.created_at >= :from AND wr.created_at < :to
                   AND (:projectId IS NULL OR w.project_id = :projectId)
                 GROUP BY wr.workflow_id
                 ORDER BY run_count DESC, wr.workflow_id ASC
                 LIMIT %d
                """.formatted(LIMIT), params, (rs, rowNum) -> {
            OperationsAnalyticsOverview.WorkflowUsage item = new OperationsAnalyticsOverview.WorkflowUsage();
            long runCount = rs.getLong("run_count");
            long successCount = rs.getLong("success_count");
            item.setWorkflowId(rs.getLong("workflow_id"));
            item.setWorkflowName(rs.getString("workflow_name"));
            item.setRunCount(runCount);
            item.setSuccessCount(successCount);
            item.setFailureCount(rs.getLong("failure_count"));
            item.setSuccessRate(rate(successCount, runCount));
            item.setAvgElapsedMs(round2(rs.getDouble("avg_elapsed_ms")));
            return item;
        });
    }

    private List<OperationsAnalyticsOverview.McpToolUsage> loadMcpTools(MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT mcp_server_id, tool_name,
                       COUNT(*) call_count,
                       SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) failure_count,
                       COALESCE(AVG(elapsed_ms), 0) avg_elapsed_ms,
                       MAX(CASE WHEN success = 0 THEN error_summary ELSE '' END) last_error_summary
                  FROM t_mcp_tool_call_audit
                 WHERE deleted = 0
                   AND created_at >= :from AND created_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                 GROUP BY mcp_server_id, tool_name
                 ORDER BY failure_count DESC, call_count DESC
                 LIMIT %d
                """.formatted(LIMIT), params, (rs, rowNum) -> {
            OperationsAnalyticsOverview.McpToolUsage item = new OperationsAnalyticsOverview.McpToolUsage();
            long callCount = rs.getLong("call_count");
            long failureCount = rs.getLong("failure_count");
            item.setMcpServerId(rs.getLong("mcp_server_id"));
            item.setToolName(rs.getString("tool_name"));
            item.setCallCount(callCount);
            item.setFailureCount(failureCount);
            item.setFailureRate(rate(failureCount, callCount));
            item.setAvgElapsedMs(round2(rs.getDouble("avg_elapsed_ms")));
            item.setLastErrorSummary(rs.getString("last_error_summary"));
            return item;
        });
    }

    private List<OperationsAnalyticsOverview.ErrorUsage> loadErrors(MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT 'CONVERSATION' source_type,
                       COALESCE(NULLIF(error_code, ''), 'UNKNOWN') error_code,
                       COALESCE(MAX(error_message), '') error_message,
                       COUNT(*) error_count,
                       MAX(started_at) last_seen_at
                  FROM t_conversation_trace
                 WHERE deleted = 0
                   AND status IN ('ERROR','TIMEOUT','BACKEND_ERROR','FAILED')
                   AND started_at >= :from AND started_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                 GROUP BY COALESCE(NULLIF(error_code, ''), 'UNKNOWN')
                 ORDER BY error_count DESC
                 LIMIT %d
                """.formatted(LIMIT), params, (rs, rowNum) -> {
            OperationsAnalyticsOverview.ErrorUsage item = new OperationsAnalyticsOverview.ErrorUsage();
            item.setSourceType(rs.getString("source_type"));
            item.setErrorCode(rs.getString("error_code"));
            item.setErrorMessage(rs.getString("error_message"));
            item.setCount(rs.getLong("error_count"));
            Timestamp lastSeenAt = rs.getTimestamp("last_seen_at");
            item.setLastSeenAt(lastSeenAt == null ? null : lastSeenAt.toLocalDateTime().toString());
            return item;
        });
    }

    private List<OperationsAnalyticsOverview.SlowLlmCall> loadSlowLlmCalls(MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT trace_id, agent_id, model_id, latency_ms,
                       COALESCE(input_tokens + output_tokens, 0) total_tokens,
                       success, error_code, created_at
                  FROM t_llm_call_stat
                 WHERE deleted = 0
                   AND created_at >= :from AND created_at < :to
                   AND (:projectId IS NULL OR project_id = :projectId)
                 ORDER BY latency_ms DESC, created_at DESC
                 LIMIT %d
                """.formatted(LIMIT), params, (rs, rowNum) -> {
            OperationsAnalyticsOverview.SlowLlmCall item = new OperationsAnalyticsOverview.SlowLlmCall();
            item.setTraceId(rs.getString("trace_id"));
            item.setAgentId(nullableLong(rs.getLong("agent_id"), rs.wasNull()));
            item.setModelId(rs.getString("model_id"));
            item.setLatencyMs(rs.getLong("latency_ms"));
            item.setTotalTokens(rs.getLong("total_tokens"));
            item.setSuccess(rs.getInt("success") == 1);
            item.setErrorCode(rs.getString("error_code"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime().toString());
            return item;
        });
    }

    private List<OperationsAnalyticsOverview.RiskConversation> loadRiskConversations(MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT c.trace_id, c.agent_id, c.agent_name, c.status,
                       c.rag_triggered, c.mcp_triggered, c.error_code, c.error_message, c.started_at,
                       COUNT(r.id) rag_hit_count
                  FROM t_conversation_trace c
                  LEFT JOIN t_conversation_rag_trace r ON r.trace_id = c.trace_id AND r.deleted = 0
                 WHERE c.deleted = 0
                   AND c.started_at >= :from AND c.started_at < :to
                   AND (:projectId IS NULL OR c.project_id = :projectId)
                   AND (c.status IN ('ERROR','TIMEOUT','BACKEND_ERROR','FAILED')
                        OR (c.rag_triggered = 1 AND r.id IS NULL)
                        OR c.mcp_triggered = 1)
                 GROUP BY c.trace_id, c.agent_id, c.agent_name, c.status, c.rag_triggered,
                          c.mcp_triggered, c.error_code, c.error_message, c.started_at
                 ORDER BY CASE WHEN c.status IN ('ERROR','TIMEOUT','BACKEND_ERROR','FAILED') THEN 0 ELSE 1 END,
                          c.started_at DESC
                 LIMIT %d
                """.formatted(LIMIT), params, (rs, rowNum) -> {
            OperationsAnalyticsOverview.RiskConversation item = new OperationsAnalyticsOverview.RiskConversation();
            item.setTraceId(rs.getString("trace_id"));
            item.setAgentId(nullableLong(rs.getLong("agent_id"), rs.wasNull()));
            item.setAgentName(rs.getString("agent_name"));
            item.setStatus(rs.getString("status"));
            item.setRagTriggered(rs.getInt("rag_triggered") == 1);
            item.setRagHit(rs.getLong("rag_hit_count") > 0);
            item.setMcpTriggered(rs.getInt("mcp_triggered") == 1);
            item.setErrorCode(rs.getString("error_code"));
            item.setErrorMessage(rs.getString("error_message"));
            Timestamp startedAt = rs.getTimestamp("started_at");
            item.setStartedAt(startedAt == null ? null : startedAt.toLocalDateTime().toString());
            return item;
        });
    }

    private MapSqlParameterSource params(Long projectId, LocalDateTime from, LocalDateTime to) {
        return new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("from", Timestamp.valueOf(from))
                .addValue("to", Timestamp.valueOf(to));
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.queryForMap(sql, params);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Number value = jdbcTemplate.queryForObject(sql, params, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private long lng(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return round4((double) numerator / denominator);
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Long nullableLong(long value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private OperationsAnalyticsOverview.DiagnosticIssue issue(
            String type,
            String severity,
            String title,
            String description,
            long impactCount,
            double rate,
            String primarySignal,
            String recommendation,
            String traceId,
            String lastSeenAt) {
        OperationsAnalyticsOverview.DiagnosticIssue issue = new OperationsAnalyticsOverview.DiagnosticIssue();
        issue.setType(type);
        issue.setSeverity(severity);
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setImpactCount(impactCount);
        issue.setRate(rate);
        issue.setPrimarySignal(primarySignal);
        issue.setRecommendation(recommendation);
        issue.setTraceId(traceId);
        issue.setLastSeenAt(lastSeenAt);
        return issue;
    }

    private String severity(double rate) {
        if (rate >= 0.2) {
            return "HIGH";
        }
        if (rate >= 0.1) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private <T> T first(List<T> items) {
        return items == null || items.isEmpty() ? null : items.get(0);
    }

    private String firstTrace(List<OperationsAnalyticsOverview.RiskConversation> conversations) {
        OperationsAnalyticsOverview.RiskConversation conversation = first(conversations);
        return conversation == null ? null : conversation.getTraceId();
    }

    private String firstRagMissTrace(List<OperationsAnalyticsOverview.RiskConversation> conversations) {
        if (conversations == null) {
            return null;
        }
        return conversations.stream()
                .filter(item -> Boolean.TRUE.equals(item.getRagTriggered()) && !Boolean.TRUE.equals(item.getRagHit()))
                .map(OperationsAnalyticsOverview.RiskConversation::getTraceId)
                .findFirst()
                .orElse(null);
    }
}
