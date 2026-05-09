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
        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        components.put("mysql", checkMysql());
        components.put("redis", checkRedis());
        components.put("pgvector", checkPgvector());

        boolean allUp = components.values().stream()
                .allMatch(component -> UP.equals(component.status()));
        return new HealthResponse(allUp ? UP : DOWN, components);
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

    public record HealthResponse(String status, Map<String, ComponentHealth> components) {
    }

    public record ComponentHealth(String status, String error) {
        static ComponentHealth up() {
            return new ComponentHealth(UP, null);
        }

        static ComponentHealth down(String error) {
            return new ComponentHealth(DOWN, error);
        }
    }
}
