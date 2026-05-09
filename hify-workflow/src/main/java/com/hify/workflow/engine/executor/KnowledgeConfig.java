package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

public record KnowledgeConfig(
        Long knowledgeBaseId,
        String query,
        Integer topK,
        String outputVariable
) implements NodeConfigDef {
}
