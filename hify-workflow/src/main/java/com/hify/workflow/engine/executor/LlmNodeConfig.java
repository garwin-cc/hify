package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

import java.math.BigDecimal;

public record LlmNodeConfig(
        Long modelConfigId,
        String prompt,
        String outputVariable,
        BigDecimal temperature,
        Integer maxTokens
) implements NodeConfigDef {
}
