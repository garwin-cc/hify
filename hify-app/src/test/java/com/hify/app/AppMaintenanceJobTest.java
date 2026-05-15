package com.hify.app;

import com.hify.app.domain.AppJobRunLogger;
import com.hify.app.domain.AppMaintenanceJob;
import com.hify.support.HifyMockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppMaintenanceJobTest extends HifyMockIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppMaintenanceJob appMaintenanceJob;

    @Autowired
    private AppJobRunLogger appJobRunLogger;

    @Test
    void should_revokeExpiredSessionsAndRecordJobLog() {
        jdbcTemplate.update("""
                INSERT INTO t_user_session (id, user_id, token_hash, expires_at, revoked, created_by, deleted)
                VALUES (9101, 1, 'expired-token', ?, 0, 0, 0)
                """, LocalDateTime.now().minusDays(1));

        int affected = appMaintenanceJob.cleanupExpiredSessions();

        Integer revoked = jdbcTemplate.queryForObject(
                "SELECT revoked FROM t_user_session WHERE id = 9101",
                Integer.class);
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_app_job_run_log WHERE job_name = 'EXPIRED_SESSION_CLEANUP' AND status = 'SUCCESS'",
                Integer.class);

        assertThat(affected).isEqualTo(1);
        assertThat(revoked).isEqualTo(1);
        assertThat(logCount).isEqualTo(1);
    }

    @Test
    void should_recordFailedJobLog_when_jobThrowsException() {
        assertThatThrownBy(() -> appJobRunLogger.run("BROKEN_JOB", () -> {
            throw new IllegalStateException("broken maintenance");
        })).isInstanceOf(IllegalStateException.class);

        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_app_job_run_log WHERE job_name = 'BROKEN_JOB' AND status = 'FAILED'",
                Integer.class);
        String error = jdbcTemplate.queryForObject(
                "SELECT error_summary FROM t_app_job_run_log WHERE job_name = 'BROKEN_JOB' AND status = 'FAILED'",
                String.class);

        assertThat(logCount).isEqualTo(1);
        assertThat(error).contains("broken maintenance");
    }

    @Test
    void should_markTimedOutKnowledgeDocumentsFailedAndRecordJobLog() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_knowledge_document (
                    id BIGINT PRIMARY KEY,
                    parse_status VARCHAR(32) NOT NULL,
                    process_stage VARCHAR(64) NOT NULL DEFAULT '',
                    process_progress INT NOT NULL DEFAULT 0,
                    error_code VARCHAR(64) NOT NULL DEFAULT '',
                    error_message VARCHAR(1000) NOT NULL DEFAULT '',
                    failed_stage VARCHAR(64) NOT NULL DEFAULT '',
                    retryable TINYINT NOT NULL DEFAULT 0,
                    cancel_requested TINYINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL,
                    deleted TINYINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document
                (id, parse_status, process_stage, process_progress, updated_at, deleted)
                VALUES (9201, 'PROCESSING', 'EMBEDDING', 40, ?, 0)
                """, LocalDateTime.now().minusHours(2));

        int affected = appMaintenanceJob.recoverTimedOutKnowledgeDocuments();

        String status = jdbcTemplate.queryForObject(
                "SELECT parse_status FROM t_knowledge_document WHERE id = 9201",
                String.class);
        String errorCode = jdbcTemplate.queryForObject(
                "SELECT error_code FROM t_knowledge_document WHERE id = 9201",
                String.class);
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_app_job_run_log WHERE job_name = 'KNOWLEDGE_PROCESSING_TIMEOUT_RECOVERY' AND status = 'SUCCESS'",
                Integer.class);

        assertThat(affected).isEqualTo(1);
        assertThat(status).isEqualTo("FAILED");
        assertThat(errorCode).isEqualTo("TASK_TIMEOUT");
        assertThat(logCount).isEqualTo(1);
    }

    @Test
    void should_markTimedOutWorkflowRunsFailedAndRecordJobLog() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_workflow_run (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(32) NOT NULL,
                    error VARCHAR(1000),
                    timeout_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL,
                    deleted TINYINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO t_workflow_run (id, status, timeout_at, updated_at, deleted)
                VALUES (9301, 'RUNNING', ?, ?, 0)
                """, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(10));

        int affected = appMaintenanceJob.markTimedOutWorkflowRunsFailed();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_workflow_run WHERE id = 9301",
                String.class);
        String error = jdbcTemplate.queryForObject(
                "SELECT error FROM t_workflow_run WHERE id = 9301",
                String.class);
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_app_job_run_log WHERE job_name = 'WORKFLOW_TIMEOUT_CHECK' AND status = 'SUCCESS'",
                Integer.class);

        assertThat(affected).isEqualTo(1);
        assertThat(status).isEqualTo("FAILED");
        assertThat(error).contains("运行超时");
        assertThat(logCount).isEqualTo(1);
    }
}
