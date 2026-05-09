package com.hify.workflow.domain.config;

public record ConditionRuleConfig(
        String expression,
        String targetNodeKey
) {
}
