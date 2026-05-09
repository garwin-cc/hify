package com.hify.workflow.engine;

/** 工作流执行引擎使用的节点快照。 */
public record WorkflowNode(
        String nodeKey,
        String nodeType,
        String name
) {
}
