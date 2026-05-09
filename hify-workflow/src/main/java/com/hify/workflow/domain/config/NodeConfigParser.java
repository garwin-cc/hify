package com.hify.workflow.domain.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.engine.NodeConfigDef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NodeConfigParser {

    private final ObjectMapper objectMapper;

    public NodeConfig parse(String nodeType, JsonNode config) {
        JsonNode safeConfig = config == null || config.isNull()
                ? objectMapper.createObjectNode()
                : config;
        try {
            return switch (WorkflowNodeType.parse(nodeType)) {
                case START -> objectMapper.treeToValue(safeConfig, EmptyNodeConfig.class);
                case END -> objectMapper.treeToValue(safeConfig, EndNodeConfig.class);
                case LLM -> objectMapper.treeToValue(safeConfig, LlmNodeConfig.class);
                case TOOL -> objectMapper.treeToValue(safeConfig, ToolNodeConfig.class);
                case CONDITION -> objectMapper.treeToValue(safeConfig, ConditionNodeConfig.class);
                case API_CALL -> objectMapper.treeToValue(safeConfig, ApiCallNodeConfig.class);
                case KNOWLEDGE -> objectMapper.treeToValue(safeConfig, KnowledgeNodeConfig.class);
                case REPLY -> objectMapper.treeToValue(safeConfig, ReplyNodeConfig.class);
            };
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "节点配置格式不正确: type=" + nodeType, e);
        }
    }

    public String validateAndSerialize(String nodeType, JsonNode config) {
        NodeConfig parsed = parse(nodeType, config);
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "节点配置序列化失败", e);
        }
    }

    public JsonNode deserialize(String configJson) {
        try {
            return objectMapper.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "节点配置不是合法 JSON", e);
        }
    }

    public NodeConfigDef parseExecutionConfig(String nodeType, String configJson) {
        JsonNode safeConfig = deserialize(configJson);
        try {
            return switch (WorkflowNodeType.parse(nodeType)) {
                case START -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.StartNodeConfig.class);
                case END -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.EndNodeConfig.class);
                case LLM -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.LlmNodeConfig.class);
                case CONDITION -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.ConditionNodeConfig.class);
                case API_CALL -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.ApiCallConfig.class);
                case KNOWLEDGE -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.KnowledgeConfig.class);
                case TOOL, REPLY -> throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                        "暂不支持执行节点类型: " + nodeType);
            };
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "节点执行配置格式不正确: type=" + nodeType, e);
        }
    }
}
