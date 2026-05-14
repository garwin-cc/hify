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
            String outputVariable = outputVariable(node);
            if (StringUtils.hasText(outputVariable)) {
                variables.add(variable(node.getNodeKey(), node.getNodeType(), outputVariable, node.getName()));
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

    private String outputVariable(WorkflowNodePo node) {
        if ("START".equalsIgnoreCase(node.getNodeType())) {
            return null;
        }
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            JsonNode output = config.path("outputVariable");
            if (output.isTextual() && StringUtils.hasText(output.asText())) {
                return output.asText();
            }
        } catch (Exception e) {
            log.warn("failed to parse workflow node config for variables workflowId={} nodeKey={}: {}",
                    node.getWorkflowId(), node.getNodeKey(), e.getMessage());
        }
        return switch (node.getNodeType().toUpperCase()) {
            case "LLM" -> "answer";
            case "KNOWLEDGE" -> "chunks";
            case "API_CALL", "CODE_TASK", "CONDITION" -> "output";
            case "HUMAN_REVIEW" -> "result";
            case "END" -> "output";
            default -> null;
        };
    }
}
