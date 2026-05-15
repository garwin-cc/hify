package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableAssignerNodeExecutorTest {

    @Test
    void should_resolve_templates_and_write_assignments_when_variable_assigner_executes() {
        VariableAssignerNodeExecutor executor = new VariableAssignerNodeExecutor();
        ExecutionContext ctx = new ExecutionContext(99L, "生成周报");
        Map<String, String> assignments = new LinkedHashMap<>();
        assignments.put("summary", "任务：{{start.userMessage}}");
        assignments.put("status", "READY");

        executor.execute(
                new WorkflowNode("assign", "VARIABLE_ASSIGNER", "变量赋值"),
                new VariableAssignerConfig(assignments),
                ctx);

        assertThat(ctx.get("assign", "summary")).isEqualTo("任务：生成周报");
        assertThat(ctx.get("assign", "status")).isEqualTo("READY");
    }

    @Test
    void should_reject_invalid_variable_name_when_assignment_key_is_invalid() {
        VariableAssignerNodeExecutor executor = new VariableAssignerNodeExecutor();
        ExecutionContext ctx = new ExecutionContext(99L, "生成周报");

        assertThatThrownBy(() -> executor.execute(
                new WorkflowNode("assign", "VARIABLE_ASSIGNER", "变量赋值"),
                new VariableAssignerConfig(Map.of("bad.name", "value")),
                ctx))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("变量名不合法");
    }
}
