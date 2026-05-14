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
}
