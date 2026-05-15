package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.workflow.api.WorkflowVariableResp;
import com.hify.workflow.infra.WorkflowNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowVariableService {

    private final WorkflowNodeMapper workflowNodeMapper;
    private final ObjectMapper objectMapper;

    public List<WorkflowVariableResp> listVariables(Long workflowId) {
        List<WorkflowVariableResp> variables = new ArrayList<>();
        variables.add(variable("start", "START", "userMessage", "用户输入"));
        List<WorkflowNodePo> nodes = workflowNodeMapper.selectList(Wrappers.lambdaQuery(WorkflowNodePo.class)
                .eq(WorkflowNodePo::getWorkflowId, workflowId)
                .orderByAsc(WorkflowNodePo::getId));
        for (WorkflowNodePo node : nodes) {
            List<String> outputVariables = outputVariables(node);
            for (String outputVariable : outputVariables) {
                if (StringUtils.hasText(outputVariable)) {
                    variables.add(variable(node.getNodeKey(), node.getNodeType(), outputVariable, node.getName()));
                }
            }
            variables.add(variable(node.getNodeKey(), node.getNodeType(), "error", node.getName() + " 错误"));
        }
        return variables;
    }

    private WorkflowVariableResp variable(String nodeKey, String nodeType, String variable, String label) {
        WorkflowVariableResp resp = new WorkflowVariableResp();
        resp.setNodeKey(nodeKey);
        resp.setNodeType(nodeType);
        resp.setVariable(variable);
        resp.setExpression("{{" + nodeKey + "." + variable + "}}");
        resp.setLabel(label);
        return resp;
    }

    private List<String> outputVariables(WorkflowNodePo node) {
        if ("START".equalsIgnoreCase(node.getNodeType())) {
            return List.of();
        }
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            if ("VARIABLE_ASSIGNER".equalsIgnoreCase(node.getNodeType())) {
                JsonNode assignments = config.path("assignments");
                if (assignments.isObject()) {
                    List<String> names = new ArrayList<>();
                    assignments.fieldNames().forEachRemaining(names::add);
                    return names;
                }
            }
            if ("REPLY".equalsIgnoreCase(node.getNodeType())) {
                return List.of("reply");
            }
            JsonNode output = config.path("outputVariable");
            if (output.isTextual() && StringUtils.hasText(output.asText())) {
                return List.of(output.asText());
            }
        } catch (Exception e) {
            log.warn("failed to parse workflow node config for variables workflowId={} nodeKey={}: {}",
                    node.getWorkflowId(), node.getNodeKey(), e.getMessage());
        }
        String fallback = switch (node.getNodeType().toUpperCase()) {
            case "LLM" -> "answer";
            case "KNOWLEDGE" -> "chunks";
            case "API_CALL", "CODE_TASK", "CONDITION", "TOOL" -> "output";
            case "HUMAN_REVIEW" -> "result";
            case "REPLY" -> "reply";
            case "END" -> "output";
            default -> null;
        };
        return StringUtils.hasText(fallback) ? List.of(fallback) : List.of();
    }
}
