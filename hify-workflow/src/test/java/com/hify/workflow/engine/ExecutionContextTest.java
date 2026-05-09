package com.hify.workflow.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContextTest {

    @Test
    void initializesUserMessageAndResolvesVariablesInInsertionOrder() {
        ExecutionContext context = new ExecutionContext(1001L, "订单怎么退款");

        context.set("llm", "answer", "请提供订单号");
        context.set("tool", "status", 200);

        String resolved = context.resolve("""
                用户问题：{{start.userMessage}}
                LLM 输出：{{llm.answer}}
                工具状态：{{tool.status}}
                缺失变量：{{missing.value}}
                """);

        assertThat(context.getWorkflowRunId()).isEqualTo(1001L);
        assertThat(context.get("start", "userMessage")).isEqualTo("订单怎么退款");
        assertThat(resolved).contains("用户问题：订单怎么退款");
        assertThat(resolved).contains("LLM 输出：请提供订单号");
        assertThat(resolved).contains("工具状态：200");
        assertThat(resolved).contains("缺失变量：{{missing.value}}");
        assertThat(context.snapshot().keySet())
                .containsExactly("start.userMessage", "llm.answer", "tool.status");
    }

    @Test
    void snapshotReturnsReadOnlyView() {
        ExecutionContext context = new ExecutionContext(1002L, "hello");
        Map<String, Object> snapshot = context.snapshot();

        assertThatThrownBy(() -> snapshot.put("x.y", "z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
