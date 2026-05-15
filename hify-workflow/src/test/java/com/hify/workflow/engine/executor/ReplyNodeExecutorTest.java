package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.WorkflowNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyNodeExecutorTest {

    @Test
    void should_resolve_content_and_write_reply_when_reply_node_executes() {
        ReplyNodeExecutor executor = new ReplyNodeExecutor();
        ExecutionContext ctx = new ExecutionContext(99L, "生成周报");

        executor.execute(
                new WorkflowNode("reply", "REPLY", "中间回复"),
                new ReplyConfig("正在处理：{{start.userMessage}}"),
                ctx);

        assertThat(ctx.get("reply", "reply")).isEqualTo("正在处理：生成周报");
    }
}
