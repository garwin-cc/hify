package com.hify.app.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppLogArchiveService {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "apikey", "api_key", "password", "token", "authorization", "secret", "credential", "authconfig");
    private static final int MAX_PAYLOAD_LENGTH = 16_000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AppMaintenanceProperties properties;

    public int archiveAndDelete(String archiveType, String sourceTable, LocalDateTime before) {
        if (!properties.isArchiveEnabled() || !tableExists("t_ops_log_archive") || !tableExists(sourceTable)) {
            return deleteExpired(sourceTable, before);
        }
        int batchSize = Math.max(1, properties.getArchiveBatchSize());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + sourceTable + " WHERE created_at < ? AND deleted = 0 ORDER BY id LIMIT ?",
                before, batchSize);
        if (rows.isEmpty()) {
            return 0;
        }
        for (Map<String, Object> row : rows) {
            Number id = (Number) row.get("id");
            jdbcTemplate.update("""
                    INSERT INTO t_ops_log_archive
                    (archive_type, source_table, source_id, payload_json, archived_at, created_at, updated_at, created_by, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)
                    """, archiveType, sourceTable, id.longValue(), toPayload(row),
                    LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        }
        String placeholders = String.join(",", rows.stream().map(row -> "?").toList());
        Object[] ids = rows.stream()
                .map(row -> ((Number) row.get("id")).longValue())
                .toArray();
        return jdbcTemplate.update("DELETE FROM " + sourceTable + " WHERE id IN (" + placeholders + ")", ids);
    }

    public ArchiveHealth health() {
        if (!tableExists("t_ops_log_archive")) {
            return new ArchiveHealth("UP", false, 0, null, null);
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_ops_log_archive WHERE deleted = 0",
                Integer.class);
        LocalDateTime latest = jdbcTemplate.queryForObject(
                "SELECT MAX(archived_at) FROM t_ops_log_archive WHERE deleted = 0",
                LocalDateTime.class);
        return new ArchiveHealth("UP", properties.isArchiveEnabled(), total == null ? 0 : total,
                latest, properties.getArchiveRetentionDays());
    }

    private int deleteExpired(String table, LocalDateTime before) {
        if (!tableExists(table)) {
            return 0;
        }
        return jdbcTemplate.update("DELETE FROM " + table + " WHERE created_at < ? AND deleted = 0", before);
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

    private String toPayload(Map<String, Object> row) {
        try {
            String json = objectMapper.writeValueAsString(sanitize(row));
            if (json.length() <= MAX_PAYLOAD_LENGTH) {
                return json;
            }
            Map<String, Object> truncated = new LinkedHashMap<>();
            truncated.put("truncated", true);
            truncated.put("length", json.length());
            truncated.put("prefix", json.substring(0, MAX_PAYLOAD_LENGTH));
            return objectMapper.writeValueAsString(truncated);
        } catch (JsonProcessingException e) {
            return "{\"archiveError\":\"payload serialization failed\"}";
        }
    }

    private Map<String, Object> sanitize(Map<String, Object> row) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        row.forEach((key, value) -> sanitized.put(key, sensitive(key) ? "******" : value));
        return sanitized;
    }

    private boolean sensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }

    public record ArchiveHealth(String status, boolean enabled, int archivedRows, LocalDateTime latestArchivedAt,
                                Integer retentionDays) {
    }
}
