package com.hify.app.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemInitializationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final SystemInitializationProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        ensureDefaultWorkspaceAndProject();
        ensureDefaultRateLimitQuotas();
        ensureDefaultProviderAndModels();
    }

    private void ensureDefaultWorkspaceAndProject() {
        if (!tableExists("t_workspace") || !tableExists("t_project")) {
            return;
        }
        insertIfMissing("t_workspace",
                "INSERT INTO t_workspace (id, name, code, status, created_by, deleted) VALUES (1, '默认空间', 'default', 'ACTIVE', 0, 0)",
                "SELECT COUNT(*) FROM t_workspace WHERE id = 1 AND deleted = 0");
        insertIfMissing("t_project",
                "INSERT INTO t_project (id, workspace_id, name, code, status, created_by, deleted) VALUES (1, 1, '默认项目', 'default', 'ACTIVE', 0, 0)",
                "SELECT COUNT(*) FROM t_project WHERE id = 1 AND deleted = 0");
    }

    private void ensureDefaultRateLimitQuotas() {
        if (!tableExists("t_rate_limit_quota")) {
            return;
        }
        List<QuotaSeed> seeds = List.of(
                new QuotaSeed("USER", 60, "默认用户调用频率"),
                new QuotaSeed("APP", 300, "默认应用调用频率"),
                new QuotaSeed("API_KEY", 120, "默认 API Key 调用频率"),
                new QuotaSeed("PROVIDER", 600, "默认 Provider 调用频率"),
                new QuotaSeed("AGENT", 120, "默认 Agent 调用频率")
        );
        for (QuotaSeed seed : seeds) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM t_rate_limit_quota
                    WHERE scope_type = 'GLOBAL' AND scope_id = 0 AND dimension = ? AND quota_key = '*' AND deleted = 0
                    """, Integer.class, seed.dimension());
            if (count != null && count > 0) {
                continue;
            }
            jdbcTemplate.update("""
                    INSERT INTO t_rate_limit_quota
                    (scope_type, scope_id, dimension, quota_key, limit_count, window_sec, fail_open, enabled, description, created_by, deleted)
                    VALUES ('GLOBAL', 0, ?, '*', ?, 60, 1, 1, ?, 0, 0)
                    """, seed.dimension(), seed.limitCount(), seed.description());
        }
    }

    private void ensureDefaultProviderAndModels() {
        SystemInitializationProperties.DefaultProvider config = properties.getDefaultProvider();
        if (!config.isEnabled() || !tableExists("t_provider") || !tableExists("t_model_config")) {
            return;
        }
        if (!StringUtils.hasText(config.getChatModelId())) {
            log.warn("skip default provider initialization because chat model id is empty");
            return;
        }
        Long providerId = findProviderId(config.getName());
        if (providerId == null) {
            jdbcTemplate.update("""
                    INSERT INTO t_provider (name, type, base_url, auth_config, enabled, sort_order, created_by, deleted)
                    VALUES (?, ?, ?, ?, 1, 0, 0, 0)
                    """, config.getName(), config.getType().toUpperCase(), value(config.getBaseUrl()), authConfig(config.getApiKey()));
            providerId = findProviderId(config.getName());
            log.info("initialized default provider id={} name={}", providerId, config.getName());
        }
        Long chatModelId = ensureModel(providerId, config.getChatModelName(), config.getChatModelId(), "CHAT");
        if (tableExists("t_model_default_policy") && chatModelId != null) {
            ensureModelPolicy("CHAT", chatModelId);
        }
        if (StringUtils.hasText(config.getEmbeddingModelId())) {
            Long embeddingModelId = ensureModel(providerId, config.getEmbeddingModelName(), config.getEmbeddingModelId(), "EMBEDDING");
            if (tableExists("t_model_default_policy") && embeddingModelId != null) {
                ensureModelPolicy("EMBEDDING", embeddingModelId);
            }
        }
    }

    private Long ensureModel(Long providerId, String name, String modelId, String modelType) {
        Long existing = queryLong("""
                SELECT id FROM t_model_config
                WHERE provider_id = ? AND model_id = ? AND model_type = ? AND deleted = 0
                LIMIT 1
                """, providerId, modelId, modelType);
        if (existing != null) {
            return existing;
        }
        jdbcTemplate.update("""
                INSERT INTO t_model_config
                (provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted)
                VALUES (?, ?, ?, ?, 8192, '{}', 1, 0, 0, 0)
                """, providerId, name, modelId, modelType);
        return queryLong("""
                SELECT id FROM t_model_config
                WHERE provider_id = ? AND model_id = ? AND model_type = ? AND deleted = 0
                LIMIT 1
                """, providerId, modelId, modelType);
    }

    private void ensureModelPolicy(String modelType, Long modelConfigId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_model_default_policy
                WHERE scope_type = 'GLOBAL' AND scope_id = 0 AND model_type = ? AND provider_type = '' AND deleted = 0
                """, Integer.class, modelType);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_model_default_policy
                (scope_type, scope_id, model_type, provider_type, model_config_id, enabled, created_by, deleted)
                VALUES ('GLOBAL', 0, ?, '', ?, 1, 0, 0)
                """, modelType, modelConfigId);
    }

    private Long findProviderId(String name) {
        return queryLong("SELECT id FROM t_provider WHERE name = ? AND deleted = 0 LIMIT 1", name);
    }

    private void insertIfMissing(String table, String insertSql, String countSql) {
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(insertSql);
        log.info("initialized default row table={}", table);
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

    private Long queryLong(String sql, Object... args) {
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String authConfig(String apiKey) {
        return "{\"apiKey\":\"" + value(apiKey).replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private record QuotaSeed(String dimension, int limitCount, String description) {
    }
}
