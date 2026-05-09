package com.hify.workflow.domain.config;

public record KnowledgeNodeConfig(
        Long knowledgeBaseId,
        String query,
        Integer topK,
        String outputVariable
) implements NodeConfig {
}
