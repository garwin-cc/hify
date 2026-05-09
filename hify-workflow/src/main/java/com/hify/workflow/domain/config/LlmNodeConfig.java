package com.hify.workflow.domain.config;

import java.math.BigDecimal;

public record LlmNodeConfig(
        String prompt,
        Long modelConfigId,
        String outputVariable,
        BigDecimal temperature,
        Integer maxTokens
) implements NodeConfig {
}
