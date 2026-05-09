package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeExecutorRegistryTest {

    @Test
    void dispatchesExecutorByNodeTypeIgnoringCase() {
        NodeExecutor executor = new StubNodeExecutor("LLM");
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(executor));

        assertThat(registry.get("llm")).isSameAs(executor);
    }

    @Test
    void rejectsUnknownNodeType() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(new StubNodeExecutor("LLM")));

        assertThatThrownBy(() -> registry.get("UNKNOWN"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的节点执行器");
    }

    private record StubNodeExecutor(String nodeType) implements NodeExecutor {

        @Override
        public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        }
    }
}
