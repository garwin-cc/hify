package com.hify.app.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AppJobRunLogger {

    private static final int ERROR_LIMIT = 1000;

    private final JdbcTemplate jdbcTemplate;

    public int run(String jobName, JobAction action) {
        LocalDateTime startedAt = LocalDateTime.now();
        long start = System.currentTimeMillis();
        try {
            int affectedRows = action.run();
            insert(jobName, "SUCCESS", startedAt, System.currentTimeMillis() - start, affectedRows, "");
            return affectedRows;
        } catch (RuntimeException e) {
            insert(jobName, "FAILED", startedAt, System.currentTimeMillis() - start, 0, truncate(e.getMessage()));
            throw e;
        }
    }

    public void skipped(String jobName, String reason) {
        LocalDateTime startedAt = LocalDateTime.now();
        insert(jobName, "SKIPPED", startedAt, 0, 0, truncate(reason));
    }

    private void insert(String jobName, String status, LocalDateTime startedAt, long elapsedMs,
                        int affectedRows, String errorSummary) {
        if (!tableExists("t_app_job_run_log")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_app_job_run_log
                (job_name, status, started_at, finished_at, elapsed_ms, affected_rows, error_summary, created_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)
                """, jobName, status, startedAt, LocalDateTime.now(), elapsedMs, affectedRows, errorSummary);
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

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= ERROR_LIMIT ? message : message.substring(0, ERROR_LIMIT);
    }

    @FunctionalInterface
    public interface JobAction {
        int run();
    }
}
