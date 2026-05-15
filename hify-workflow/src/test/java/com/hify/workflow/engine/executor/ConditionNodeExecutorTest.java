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

    @Test
    void should_evaluate_numeric_comparison_when_expression_uses_numbers() {
        ExecutionContext ctx = new ExecutionContext(1L, "hello");
        ctx.set("score", "value", 89.5);

        executor.execute(
                new WorkflowNode("condition", "CONDITION", "条件判断"),
                new ConditionNodeConfig("'{{score.value}}' >= 80", "matched"),
                ctx);

        assertThat(ctx.get("condition", "matched")).isEqualTo(true);
    }

    @Test
    void should_evaluate_string_matching_when_expression_uses_contains_and_prefix() {
        ExecutionContext ctx = new ExecutionContext(1L, "hello");
        ctx.set("doc", "title", "finance-report-2026");

        executor.execute(
                new WorkflowNode("condition", "CONDITION", "条件判断"),
                new ConditionNodeConfig("'{{doc.title}}' contains 'report' AND '{{doc.title}}' startsWith 'finance'", "matched"),
                ctx);

        assertThat(ctx.get("condition", "matched")).isEqualTo(true);
    }

    @Test
    void should_evaluate_empty_checks_when_expression_uses_unary_operator() {
        ExecutionContext ctx = new ExecutionContext(1L, "hello");
        ctx.set("doc", "owner", "");
        ctx.set("doc", "type", "policy");

        executor.execute(
                new WorkflowNode("condition", "CONDITION", "条件判断"),
                new ConditionNodeConfig("'{{doc.owner}}' isEmpty AND '{{doc.type}}' isNotEmpty", "matched"),
                ctx);

        assertThat(ctx.get("condition", "matched")).isEqualTo(true);
    }

    @Test
    void should_prefer_and_before_or_when_expression_combines_conditions() {
        ExecutionContext ctx = new ExecutionContext(1L, "hello");

        executor.execute(
                new WorkflowNode("condition", "CONDITION", "条件判断"),
                new ConditionNodeConfig("false AND true OR true", "matched"),
                ctx);

        assertThat(ctx.get("condition", "matched")).isEqualTo(true);
    }
}
