package com.hify.app.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final JdbcTemplate jdbcTemplate;
    private final AppJobRunLogger appJobRunLogger;
    private final AppMaintenanceProperties properties;

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
        try {
            jdbcTemplate.query("SELECT 1 FROM " + tableName + " WHERE 1 = 0", rs -> {
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
