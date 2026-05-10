package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.WorkflowNode;

public interface CodeTaskRunner {

    String executorType();

    CodeTaskResult run(WorkflowNode node, CodeTaskConfig config, ExecutionContext ctx);
}
