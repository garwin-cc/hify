package com.hify.workflow.domain.config;

public sealed interface NodeConfig permits
        EmptyNodeConfig,
        EndNodeConfig,
        LlmNodeConfig,
        ToolNodeConfig,
        ConditionNodeConfig,
        ReplyNodeConfig,
        VariableAssignerNodeConfig,
        IterationNodeConfig,
        IterationEndNodeConfig,
        ApiCallNodeConfig,
        KnowledgeNodeConfig,
        HumanReviewNodeConfig,
        CodeTaskNodeConfig {
}
