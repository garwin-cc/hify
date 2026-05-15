package com.hify.workflow.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.workflow.api.WorkflowVariableResp;
import com.hify.workflow.infra.WorkflowNodeMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowVariableServiceTest {

    @Test
    void should_expose_default_output_variable_when_tool_node_has_no_output_variable() {
        WorkflowNodePo toolNode = new WorkflowNodePo();
        toolNode.setWorkflowId(7L);
        toolNode.setNodeKey("tool");
        toolNode.setNodeType("TOOL");
        toolNode.setName("工具调用");
        toolNode.setConfig("{}");
        WorkflowNodeMapper workflowNodeMapper = mapper(List.of(toolNode));
        WorkflowVariableService service = new WorkflowVariableService(workflowNodeMapper, new ObjectMapper());

        List<WorkflowVariableResp> variables = service.listVariables(7L);

        assertThat(variables).anySatisfy(variable -> {
            assertThat(variable.getNodeKey()).isEqualTo("tool");
            assertThat(variable.getNodeType()).isEqualTo("TOOL");
            assertThat(variable.getVariable()).isEqualTo("output");
            assertThat(variable.getExpression()).isEqualTo("{{tool.output}}");
        });
    }

    private static WorkflowNodeMapper mapper(List<WorkflowNodePo> nodes) {
        return (WorkflowNodeMapper) Proxy.newProxyInstance(
                WorkflowNodeMapper.class.getClassLoader(),
                new Class<?>[]{WorkflowNodeMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return nodes;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }
}
