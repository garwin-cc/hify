package com.hify.app.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class QualityEvaluationServiceImpl implements QualityEvaluationService {

    private static final int LIMIT = 50;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public QualityEvaluationServiceImpl(@Qualifier("mysqlNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public QualityEvaluationOverview overview(Long projectId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to != null ? to : LocalDateTime.now();
        LocalDateTime start = from != null ? from : end.minusDays(7);
        if (start.isAfter(end)) {
            start = end.minusDays(7);
        }
        MapSqlParameterSource params = params(projectId, start, end);

        QualityEvaluationOverview overview = new QualityEvaluationOverview();
        fillSummary(overview.getSummary(), params);
        overview.setAgents(loadAgents(params));
        overview.setIssues(loadIssues(params, overview.getSummary().getNegativeFeedbackCount()));
        return overview;
    }

    @Override
    public List<QualitySampleResp> samples(Long projectId, String reviewStatus, LocalDateTime from, LocalDateTime to) {
        MapSqlParameterSource params = params(projectId, from, to)
                .addValue("reviewStatus", normalizeOptionalStatus(reviewStatus));
        return querySamples("""
                   AND (:projectId IS NULL OR COALESCE(f.project_id, c.project_id) = :projectId)
                   AND (:reviewStatus IS NULL OR f.review_status = :reviewStatus)
                   AND (:from IS NULL OR f.created_at >= :from)
                   AND (:to IS NULL OR f.created_at < :to)
                """, params);
    }

    private List<QualitySampleResp> querySamples(String extraWhere, MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT f.id, f.message_id, f.session_id, f.agent_id, COALESCE(c.agent_name, CONCAT('Agent ', f.agent_id)) agent_name,
                       COALESCE(f.project_id, c.project_id) project_id, COALESCE(NULLIF(f.trace_id, ''), c.trace_id) trace_id,
                       f.user_id, f.rating, f.issue_type, f.comment, f.corrected_answer, f.review_status, f.resolution_note,
                       u.content user_question, a.content assistant_answer,
                       COALESCE(c.rag_triggered, 0) rag_triggered,
                       CASE WHEN r.trace_id IS NULL THEN 0 ELSE 1 END rag_hit,
                       COALESCE(c.mcp_triggered, 0) mcp_triggered,
                       c.model_id, f.created_at, f.updated_at
                  FROM t_message_feedback f
                  JOIN t_chat_message a ON a.id = f.message_id AND a.deleted = 0
                  LEFT JOIN t_conversation_trace c ON c.trace_id = COALESCE(NULLIF(f.trace_id, ''), a.trace_id) AND c.deleted = 0
                  LEFT JOIN t_chat_message u ON u.id = c.user_message_id AND u.deleted = 0
                  LEFT JOIN (
                        SELECT trace_id FROM t_conversation_rag_trace WHERE deleted = 0 GROUP BY trace_id
                 ) r ON r.trace_id = c.trace_id
                 WHERE f.deleted = 0
                   AND f.rating = 'DISLIKE'
                %s
                 ORDER BY CASE f.review_status WHEN 'OPEN' THEN 0 WHEN 'REVIEWING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END,
                          f.created_at DESC
                 LIMIT %d
                """.formatted(extraWhere, LIMIT), params, (rs, rowNum) -> {
            QualitySampleResp item = new QualitySampleResp();
            item.setId(rs.getLong("id"));
            item.setMessageId(rs.getLong("message_id"));
            item.setSessionId(rs.getLong("session_id"));
            item.setAgentId(rs.getLong("agent_id"));
            item.setAgentName(rs.getString("agent_name"));
            item.setProjectId(nullableLong(rs.getLong("project_id"), rs.wasNull()));
            item.setTraceId(rs.getString("trace_id"));
            item.setUserId(rs.getLong("user_id"));
            item.setRating(rs.getString("rating"));
            item.setIssueType(rs.getString("issue_type"));
            item.setComment(rs.getString("comment"));
            item.setCorrectedAnswer(rs.getString("corrected_answer"));
            item.setReviewStatus(rs.getString("review_status"));
            item.setResolutionNote(rs.getString("resolution_note"));
            item.setUserQuestion(rs.getString("user_question"));
            item.setAssistantAnswer(rs.getString("assistant_answer"));
            item.setRagTriggered(rs.getInt("rag_triggered") == 1);
            item.setRagHit(rs.getInt("rag_hit") == 1);
            item.setMcpTriggered(rs.getInt("mcp_triggered") == 1);
            item.setModelId(rs.getString("model_id"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime().toString());
            item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime().toString());
            return item;
        });
    }

    @Override
    public QualitySampleResp updateSampleStatus(Long sampleId, UpdateQualitySampleStatusReq req) {
        String status = normalizeRequiredStatus(req == null ? null : req.getReviewStatus());
        String note = abbreviate(req == null ? null : req.getResolutionNote(), 1000);
        int updated = jdbcTemplate.update("""
                UPDATE t_message_feedback
                   SET review_status = :reviewStatus,
                       resolution_note = :resolutionNote,
                       resolved_at = CASE WHEN :reviewStatus IN ('RESOLVED','IGNORED') THEN CURRENT_TIMESTAMP ELSE NULL END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = :id AND deleted = 0 AND rating = 'DISLIKE'
                """, new MapSqlParameterSource()
                .addValue("id", sampleId)
                .addValue("reviewStatus", status)
                .addValue("resolutionNote", note));
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "质量样本不存在: " + sampleId);
        }
        return sampleById(sampleId);
    }

    private QualitySampleResp sampleById(Long sampleId) {
        List<QualitySampleResp> rows = querySamples("""
                   AND f.id = :id
                """, new MapSqlParameterSource().addValue("id", sampleId));
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "质量样本不存在: " + sampleId);
        }
        return rows.get(0);
    }

    private void fillSummary(QualityEvaluationOverview.Summary summary, MapSqlParameterSource params) {
        Map<String, Object> feedback = one("""
                SELECT COUNT(*) feedback_count,
                       SUM(CASE WHEN f.rating = 'DISLIKE' THEN 1 ELSE 0 END) negative_count,
                       SUM(CASE WHEN f.rating = 'DISLIKE' AND f.review_status IN ('OPEN','REVIEWING') THEN 1 ELSE 0 END) open_count
                  FROM t_message_feedback f
                  LEFT JOIN t_conversation_trace c ON c.trace_id = f.trace_id AND c.deleted = 0
                 WHERE f.deleted = 0
                   AND f.created_at >= :from AND f.created_at < :to
                   AND (:projectId IS NULL OR COALESCE(f.project_id, c.project_id) = :projectId)
                """, params);
        long feedbackCount = lng(feedback.get("feedback_count"));
        long negativeCount = lng(feedback.get("negative_count"));
        summary.setFeedbackCount(feedbackCount);
        summary.setNegativeFeedbackCount(negativeCount);
        summary.setNegativeFeedbackRate(rate(negativeCount, feedbackCount));
        summary.setOpenSampleCount(lng(feedback.get("open_count")));

        Map<String, Object> rag = one("""
                SELECT COUNT(*) rag_feedback_count,
                       SUM(CASE WHEN f.rating = 'LIKE' THEN 1 ELSE 0 END) rag_helpful_count
                  FROM t_message_feedback f
                  JOIN t_conversation_trace c ON c.trace_id = f.trace_id AND c.deleted = 0
                 WHERE f.deleted = 0
                   AND c.rag_triggered = 1
                   AND f.created_at >= :from AND f.created_at < :to
                   AND (:projectId IS NULL OR COALESCE(f.project_id, c.project_id) = :projectId)
                """, params);
        long ragFeedbackCount = lng(rag.get("rag_feedback_count"));
        long ragHelpfulCount = lng(rag.get("rag_helpful_count"));
        summary.setRagFeedbackCount(ragFeedbackCount);
        summary.setRagHelpfulCount(ragHelpfulCount);
        summary.setRagHelpfulRate(rate(ragHelpfulCount, ragFeedbackCount));
    }

    private List<QualityEvaluationOverview.AgentQuality> loadAgents(MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT f.agent_id, COALESCE(MAX(c.agent_name), CONCAT('Agent ', f.agent_id)) agent_name,
                       COUNT(*) feedback_count,
                       SUM(CASE WHEN f.rating = 'DISLIKE' THEN 1 ELSE 0 END) negative_count,
                       SUM(CASE WHEN f.rating = 'DISLIKE' AND f.review_status IN ('OPEN','REVIEWING') THEN 1 ELSE 0 END) open_count
                  FROM t_message_feedback f
                  LEFT JOIN t_conversation_trace c ON c.trace_id = f.trace_id AND c.deleted = 0
                 WHERE f.deleted = 0
                   AND f.created_at >= :from AND f.created_at < :to
                   AND (:projectId IS NULL OR COALESCE(f.project_id, c.project_id) = :projectId)
                 GROUP BY f.agent_id
                 ORDER BY negative_count DESC, feedback_count DESC
                 LIMIT 10
                """, params, (rs, rowNum) -> {
            QualityEvaluationOverview.AgentQuality item = new QualityEvaluationOverview.AgentQuality();
            long feedbackCount = rs.getLong("feedback_count");
            long negativeCount = rs.getLong("negative_count");
            item.setAgentId(rs.getLong("agent_id"));
            item.setAgentName(rs.getString("agent_name"));
            item.setFeedbackCount(feedbackCount);
            item.setNegativeFeedbackCount(negativeCount);
            item.setNegativeFeedbackRate(rate(negativeCount, feedbackCount));
            item.setOpenSampleCount(rs.getLong("open_count"));
            return item;
        });
    }

    private List<QualityEvaluationOverview.IssueQuality> loadIssues(MapSqlParameterSource params, long negativeTotal) {
        return jdbcTemplate.query("""
                SELECT COALESCE(NULLIF(f.issue_type, ''), 'OTHER') issue_type, COUNT(*) issue_count
                  FROM t_message_feedback f
                  LEFT JOIN t_conversation_trace c ON c.trace_id = f.trace_id AND c.deleted = 0
                 WHERE f.deleted = 0
                   AND f.rating = 'DISLIKE'
                   AND f.created_at >= :from AND f.created_at < :to
                   AND (:projectId IS NULL OR COALESCE(f.project_id, c.project_id) = :projectId)
                 GROUP BY COALESCE(NULLIF(f.issue_type, ''), 'OTHER')
                 ORDER BY issue_count DESC
                 LIMIT 10
                """, params, (rs, rowNum) -> {
            QualityEvaluationOverview.IssueQuality item = new QualityEvaluationOverview.IssueQuality();
            long count = rs.getLong("issue_count");
            item.setIssueType(rs.getString("issue_type"));
            item.setCount(count);
            item.setRate(rate(count, negativeTotal));
            return item;
        });
    }

    private MapSqlParameterSource params(Long projectId, LocalDateTime from, LocalDateTime to) {
        return new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("from", from == null ? null : Timestamp.valueOf(from))
                .addValue("to", to == null ? null : Timestamp.valueOf(to));
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.queryForMap(sql, params);
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
        return Math.round(((double) numerator / denominator) * 10000.0) / 10000.0;
    }

    private Long nullableLong(long value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private String normalizeOptionalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return normalizeRequiredStatus(status);
    }

    private String normalizeRequiredStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "reviewStatus 不能为空");
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of("OPEN", "REVIEWING", "RESOLVED", "IGNORED").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "reviewStatus 不支持: " + status);
        }
        return normalized;
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
