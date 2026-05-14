package com.hify.workflow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public record NodeRuntimePolicy(
        int retryTimes,
        int retryIntervalMs,
        Integer timeoutSeconds,
        String onFailure
) {

    private static final String DEFAULT_ON_FAILURE = "FAIL_RUN";

    public static NodeRuntimePolicy fromJson(ObjectMapper objectMapper, String configJson) {
        int retryTimes = 0;
        int retryIntervalMs = 0;
        Integer timeoutSeconds = null;
        String onFailure = DEFAULT_ON_FAILURE;
        try {
            JsonNode config = objectMapper.readTree(StringUtils.hasText(configJson) ? configJson : "{}");
            JsonNode runtime = config.path("runtime");
            if (!runtime.isMissingNode() && runtime.isObject()) {
                retryTimes = Math.max(0, runtime.path("retryTimes").asInt(0));
                retryIntervalMs = Math.max(0, runtime.path("retryIntervalMs").asInt(0));
                if (runtime.has("timeoutSeconds") && runtime.path("timeoutSeconds").canConvertToInt()) {
                    timeoutSeconds = Math.max(1, runtime.path("timeoutSeconds").asInt());
                }
                String strategy = runtime.path("onFailure").asText(DEFAULT_ON_FAILURE).trim().toUpperCase();
                onFailure = switch (strategy) {
                    case "CONTINUE", "ERROR_BRANCH", "HUMAN_REVIEW" -> strategy;
                    default -> DEFAULT_ON_FAILURE;
                };
            } else if (config.has("timeoutSeconds") && config.path("timeoutSeconds").canConvertToInt()) {
                timeoutSeconds = Math.max(1, config.path("timeoutSeconds").asInt());
            }
        } catch (Exception e) {
            log.warn("failed to parse workflow node runtime policy: {}", e.getMessage());
        }
        return new NodeRuntimePolicy(retryTimes, retryIntervalMs, timeoutSeconds, onFailure);
    }

    public int maxAttempts() {
        return Math.max(1, retryTimes + 1);
    }
}
