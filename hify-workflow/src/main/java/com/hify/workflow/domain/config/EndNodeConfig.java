package com.hify.workflow.domain.config;

public record EndNodeConfig(
        String outputVariable
) implements NodeConfig {
}
