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
        JsonNode businessConfig = businessConfig(safeConfig);
        try {
            return switch (WorkflowNodeType.parse(nodeType)) {
                case START -> objectMapper.treeToValue(businessConfig, EmptyNodeConfig.class);
                case END -> objectMapper.treeToValue(businessConfig, EndNodeConfig.class);
                case LLM -> objectMapper.treeToValue(businessConfig, LlmNodeConfig.class);
                case TOOL -> objectMapper.treeToValue(businessConfig, ToolNodeConfig.class);
                case CONDITION -> objectMapper.treeToValue(businessConfig, ConditionNodeConfig.class);
                case API_CALL -> objectMapper.treeToValue(businessConfig, ApiCallNodeConfig.class);
                case KNOWLEDGE -> objectMapper.treeToValue(businessConfig, KnowledgeNodeConfig.class);
                case HUMAN_REVIEW -> objectMapper.treeToValue(businessConfig, HumanReviewNodeConfig.class);
                case CODE_TASK -> objectMapper.treeToValue(businessConfig, CodeTaskNodeConfig.class);
                case REPLY -> objectMapper.treeToValue(businessConfig, ReplyNodeConfig.class);
            };
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "节点配置格式不正确: type=" + nodeType, e);
        }
    }

    public String validateAndSerialize(String nodeType, JsonNode config) {
        JsonNode safeConfig = config == null || config.isNull()
                ? objectMapper.createObjectNode()
                : config;
        parse(nodeType, safeConfig);
        try {
            return objectMapper.writeValueAsString(safeConfig);
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
        JsonNode safeConfig = businessConfig(deserialize(configJson));
        try {
            return switch (WorkflowNodeType.parse(nodeType)) {
                case START -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.StartNodeConfig.class);
                case END -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.EndNodeConfig.class);
                case LLM -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.LlmNodeConfig.class);
                case TOOL -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.ToolConfig.class);
                case CONDITION -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.ConditionNodeConfig.class);
                case API_CALL -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.ApiCallConfig.class);
                case KNOWLEDGE -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.KnowledgeConfig.class);
                case HUMAN_REVIEW -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.HumanReviewConfig.class);
                case CODE_TASK -> objectMapper.treeToValue(safeConfig,
                        com.hify.workflow.engine.executor.CodeTaskConfig.class);
                case REPLY -> throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                        "暂不支持执行节点类型: " + nodeType);
            };
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "节点执行配置格式不正确: type=" + nodeType, e);
        }
    }

    private JsonNode businessConfig(JsonNode config) {
        if (config == null || config.isNull() || !config.isObject() || !config.has("runtime")) {
            return config == null || config.isNull() ? objectMapper.createObjectNode() : config;
        }
        com.fasterxml.jackson.databind.node.ObjectNode copy = config.deepCopy();
        copy.remove("runtime");
        return copy;
    }
}
