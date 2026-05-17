package com.hify.app.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationsAnalyticsServiceImpl implements OperationsAnalyticsService {

    private static final int LIMIT = 10;

    private final NamedParameterJdbcTemplate jdbcTemplate;

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
        return overview;
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
}
