package com.hify.app.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppMaintenanceJob {

    private static final String EXPIRED_SESSION_CLEANUP = "EXPIRED_SESSION_CLEANUP";
    private static final String RUNTIME_LOG_CLEANUP = "RUNTIME_LOG_CLEANUP";
    private static final String AUDIT_LOG_CLEANUP = "AUDIT_LOG_CLEANUP";
    private static final String JOB_LOG_CLEANUP = "JOB_LOG_CLEANUP";
    private static final String KNOWLEDGE_PROCESSING_TIMEOUT_RECOVERY = "KNOWLEDGE_PROCESSING_TIMEOUT_RECOVERY";
    private static final String WORKFLOW_TIMEOUT_CHECK = "WORKFLOW_TIMEOUT_CHECK";
    private static final String RAG_ORPHAN_CHUNK_CLEANUP = "RAG_ORPHAN_CHUNK_CLEANUP";

    private final JdbcTemplate jdbcTemplate;
    private final AppJobRunLogger appJobRunLogger;
    private final AppMaintenanceProperties properties;
    private JdbcTemplate pgvectorJdbcTemplate;

    @Autowired(required = false)
    public void setPgvectorJdbcTemplate(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate pgvectorJdbcTemplate) {
        this.pgvectorJdbcTemplate = pgvectorJdbcTemplate;
    }

    @Scheduled(cron = "${hify.jobs.expired-session-cron:0 */30 * * * *}")
    public void cleanupExpiredSessionsScheduled() {
        if (!properties.isEnabled()) {
            return;
        }
        cleanupExpiredSessions();
    }

    @Scheduled(cron = "${hify.jobs.log-cleanup-cron:0 20 3 * * *}")
    public void cleanupLogsScheduled() {
        if (!properties.isEnabled()) {
            return;
        }
        cleanupRuntimeLogs();
        cleanupAuditLogs();
        cleanupJobLogs();
    }

    @Scheduled(cron = "${hify.jobs.timeout-recovery-cron:0 */10 * * * *}")
    public void recoverTimeoutJobsScheduled() {
        if (!properties.isEnabled()) {
            return;
        }
        recoverTimedOutKnowledgeDocuments();
        markTimedOutWorkflowRunsFailed();
    }

    @Scheduled(cron = "${hify.jobs.rag-orphan-cleanup-cron:0 40 3 * * *}")
    public void cleanupOrphanKnowledgeChunksScheduled() {
        if (!properties.isEnabled()) {
            return;
        }
        cleanupOrphanKnowledgeChunks();
    }

    public int cleanupExpiredSessions() {
        return appJobRunLogger.run(EXPIRED_SESSION_CLEANUP, () -> {
            if (!tableExists("t_user_session")) {
                appJobRunLogger.skipped(EXPIRED_SESSION_CLEANUP, "t_user_session 不存在");
                return 0;
            }
            int affected = jdbcTemplate.update("""
                    UPDATE t_user_session
                    SET revoked = 1, updated_at = ?
                    WHERE revoked = 0 AND expires_at < ? AND deleted = 0
                    """, LocalDateTime.now(), LocalDateTime.now());
            log.info("expired session cleanup affectedRows={}", affected);
            return affected;
        });
    }

    public int cleanupRuntimeLogs() {
        return appJobRunLogger.run(RUNTIME_LOG_CLEANUP, () -> {
            LocalDateTime before = LocalDateTime.now().minusDays(runtimeLogRetentionDays());
            int affected = 0;
            for (String table : runtimeLogTables()) {
                affected += cleanupTable(table, before);
            }
            log.info("runtime log cleanup affectedRows={}", affected);
            return affected;
        });
    }

    public int cleanupAuditLogs() {
        return appJobRunLogger.run(AUDIT_LOG_CLEANUP, () ->
                cleanupTable("t_audit_log", LocalDateTime.now().minusDays(Math.max(1, properties.getAuditLogRetentionDays()))));
    }

    public int cleanupJobLogs() {
        return appJobRunLogger.run(JOB_LOG_CLEANUP, () ->
                cleanupTable("t_app_job_run_log", LocalDateTime.now().minusDays(Math.max(1, properties.getJobLogRetentionDays()))));
    }

    public int recoverTimedOutKnowledgeDocuments() {
        return appJobRunLogger.run(KNOWLEDGE_PROCESSING_TIMEOUT_RECOVERY, () -> {
            if (!tableExists("t_knowledge_document")) {
                appJobRunLogger.skipped(KNOWLEDGE_PROCESSING_TIMEOUT_RECOVERY, "t_knowledge_document 不存在");
                return 0;
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minusMinutes(Math.max(1, properties.getKnowledgeProcessingTimeoutMinutes()));
            int affected = jdbcTemplate.update("""
                    UPDATE t_knowledge_document
                    SET parse_status = 'FAILED',
                        process_stage = 'FAILED',
                        process_progress = 0,
                        error_code = 'TASK_TIMEOUT',
                        failed_stage = 'PROCESSING',
                        retryable = 1,
                        cancel_requested = 0,
                        error_message = '文档处理超时，任务已恢复为失败状态，请重试',
                        updated_at = ?
                    WHERE parse_status = 'PROCESSING'
                      AND updated_at < ?
                      AND deleted = 0
                    """, now, cutoff);
            log.info("knowledge processing timeout recovery affectedRows={}", affected);
            return affected;
        });
    }

    public int markTimedOutWorkflowRunsFailed() {
        return appJobRunLogger.run(WORKFLOW_TIMEOUT_CHECK, () -> {
            if (!tableExists("t_workflow_run")) {
                appJobRunLogger.skipped(WORKFLOW_TIMEOUT_CHECK, "t_workflow_run 不存在");
                return 0;
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime timeoutBefore = now.minusMinutes(Math.max(0, properties.getWorkflowRunTimeoutGraceMinutes()));
            int affected = jdbcTemplate.update("""
                    UPDATE t_workflow_run
                    SET status = 'FAILED',
                        error = '工作流运行超时，后台巡检已标记失败，请检查失败节点后重跑',
                        finished_at = ?,
                        updated_at = ?
                    WHERE status IN ('RUNNING', 'WAITING')
                      AND timeout_at IS NOT NULL
                      AND timeout_at < ?
                      AND deleted = 0
                    """, now, now, timeoutBefore);
            log.info("workflow timeout check affectedRows={}", affected);
            return affected;
        });
    }

    public int cleanupOrphanKnowledgeChunks() {
        return appJobRunLogger.run(RAG_ORPHAN_CHUNK_CLEANUP, () -> {
            if (pgvectorJdbcTemplate == null || !tableExists("t_knowledge_document")
                    || !tableExists(pgvectorJdbcTemplate, "t_knowledge_chunk")) {
                return 0;
            }
            List<String> documentIds = jdbcTemplate.queryForList(
                    "SELECT CAST(id AS CHAR) FROM t_knowledge_document WHERE deleted = 0",
                    String.class);
            if (documentIds.isEmpty()) {
                return pgvectorJdbcTemplate.update("""
                        UPDATE t_knowledge_chunk
                        SET deleted = true, updated_at = now()
                        WHERE deleted = false
                        """);
            }
            String placeholders = String.join(",", documentIds.stream().map(id -> "?").toList());
            Object[] args = documentIds.toArray();
            int affected = pgvectorJdbcTemplate.update("""
                    UPDATE t_knowledge_chunk
                    SET deleted = true, updated_at = now()
                    WHERE deleted = false
                      AND document_id NOT IN (
                    """ + placeholders + ")");
            log.info("rag orphan chunk cleanup affectedRows={}", affected);
            return affected;
        });
    }

    private int cleanupTable(String table, LocalDateTime before) {
        if (!tableExists(table)) {
            return 0;
        }
        return jdbcTemplate.update("DELETE FROM " + table + " WHERE created_at < ? AND deleted = 0", before);
    }

    private List<String> runtimeLogTables() {
        return List.of(
                "t_conversation_trace",
                "t_conversation_llm_trace",
                "t_conversation_rag_trace",
                "t_mcp_tool_call_audit",
                "t_llm_call_stat"
        );
    }

    private int runtimeLogRetentionDays() {
        if (properties.getRuntimeLogRetentionDays() > 0) {
            return properties.getRuntimeLogRetentionDays();
        }
        return Math.max(1, properties.getLogRetentionDays());
    }

    private boolean tableExists(String tableName) {
        return tableExists(jdbcTemplate, tableName);
    }

    private boolean tableExists(JdbcTemplate template, String tableName) {
        try {
            template.query("SELECT 1 FROM " + tableName + " WHERE 1 = 0", rs -> {
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
