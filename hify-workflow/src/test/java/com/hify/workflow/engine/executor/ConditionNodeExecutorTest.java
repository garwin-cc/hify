package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.WorkflowNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionNodeExecutorTest {

    private final ConditionNodeExecutor executor = new ConditionNodeExecutor();

    @Test
    void evaluatesEqualityAfterResolvingTemplateVariables() {
        ExecutionContext ctx = new ExecutionContext(1L, "refund");
        ctx.set("classify", "intent", "refund");

        executor.execute(
                new WorkflowNode("condition", "CONDITION", "条件判断"),
                new ConditionNodeConfig("'{{classify.intent}}' == 'refund'", "matched"),
                ctx);

        assertThat(ctx.get("condition", "matched")).isEqualTo(true);
    }

    @Test
    void evaluatesInequalityAndBooleanLiterals() {
        ExecutionContext ctx = new ExecutionContext(1L, "hello");

        executor.execute(
                new WorkflowNode("condition", "CONDITION", "条件判断"),
                new ConditionNodeConfig("{{missing.value}} != false", "matched"),
                ctx);

        assertThat(ctx.get("condition", "matched")).isEqualTo(true);
    }
}
