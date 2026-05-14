package com.hify.web;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final @Qualifier("pgvectorJdbcTemplate") JdbcTemplate pgvectorJdbcTemplate;

    @GetMapping("/health")
    public HealthResponse health() {
        return deepHealth();
    }

    @GetMapping("/health/liveness")
    public HealthResponse liveness() {
        return new HealthResponse(UP, new LinkedHashMap<>());
    }

    @GetMapping("/health/readiness")
    public HealthResponse readiness() {
        Map<String, Object> components = readinessComponents();
        return new HealthResponse(overallStatus(components), components);
    }

    @GetMapping("/health/deep")
    public HealthResponse deepHealth() {
        Map<String, Object> components = readinessComponents();
        components.put("providerSummary", checkProviderSummary());
        return new HealthResponse(overallStatus(components), components);
    }

    private Map<String, Object> readinessComponents() {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("mysql", checkMysql());
        components.put("redis", checkRedis());
        components.put("pgvector", checkPgvector());
        return components;
    }

    private String overallStatus(Map<String, Object> components) {
        boolean allUp = components.values().stream()
                .map(this::componentStatus)
                .filter(Objects::nonNull)
                .allMatch(UP::equals);
        return allUp ? UP : DOWN;
    }

    private ComponentHealth checkMysql() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result)
                    ? ComponentHealth.up()
                    : ComponentHealth.down("Unexpected result: " + result);
        } catch (DataAccessException e) {
            return ComponentHealth.down(rootMessage(e));
        }
    }

    private ComponentHealth checkRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong)
                    ? ComponentHealth.up()
                    : ComponentHealth.down("Unexpected response: " + pong);
        } catch (Exception e) {
            return ComponentHealth.down(rootMessage(e));
        }
    }

    private ComponentHealth checkPgvector() {
        try {
            Integer result = pgvectorJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result)
                    ? ComponentHealth.up()
                    : ComponentHealth.down("Unexpected result: " + result);
        } catch (DataAccessException e) {
            return ComponentHealth.down(rootMessage(e));
        }
    }

    private ProviderSummaryHealth checkProviderSummary() {
        try {
            Integer enabledCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_provider WHERE enabled = 1 AND deleted = 0",
                    Integer.class);
            if (enabledCount == null || enabledCount == 0) {
                return new ProviderSummaryHealth(UP, null, 0, 0, 0);
            }
            Integer downCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM t_provider p
                    LEFT JOIN t_provider_health h ON h.provider_id = p.id
                    WHERE p.enabled = 1
                      AND p.deleted = 0
                      AND h.status = 'DOWN'
                    """, Integer.class);
            Integer unknownCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM t_provider p
                    LEFT JOIN t_provider_health h ON h.provider_id = p.id
                    WHERE p.enabled = 1
                      AND p.deleted = 0
                      AND (h.status IS NULL OR h.status IN ('UNKNOWN', 'DEGRADED'))
                    """, Integer.class);
            int down = downCount == null ? 0 : downCount;
            int unknown = unknownCount == null ? 0 : unknownCount;
            String status = down > 0 ? DOWN : UP;
            String error = down > 0 ? "存在不可用 Provider" : null;
            return new ProviderSummaryHealth(status, error, enabledCount, down, unknown);
        } catch (Exception e) {
            return new ProviderSummaryHealth(DOWN, rootMessage(e), 0, 0, 0);
        }
    }

    private String componentStatus(Object component) {
        if (component instanceof ComponentHealth health) {
            return health.status();
        }
        if (component instanceof ProviderSummaryHealth health) {
            return health.status();
        }
        return null;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank()
                ? root.getClass().getSimpleName()
                : message;
    }

    public record HealthResponse(String status, Map<String, Object> components) {
    }

    public record ComponentHealth(String status, String error) {
        static ComponentHealth up() {
            return new ComponentHealth(UP, null);
        }

        static ComponentHealth down(String error) {
            return new ComponentHealth(DOWN, error);
        }
    }

    public record ProviderSummaryHealth(String status, String error, int enabledCount, int downCount, int unknownCount) {
    }
}
