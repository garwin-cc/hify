package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.knowledge.api.KnowledgeBaseResp;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.mcp.api.McpService;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import com.hify.workflow.api.CreateWorkflowFromTemplateReq;
import com.hify.workflow.api.CreateWorkflowReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowEdgeDto;
import com.hify.workflow.api.WorkflowNodeDto;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowTemplateDetailResp;
import com.hify.workflow.api.WorkflowTemplateListItemResp;
import com.hify.workflow.api.WorkflowTemplateQuery;
import com.hify.workflow.api.WorkflowTemplateRequirementResp;
import com.hify.workflow.api.WorkflowTemplateService;
import com.hify.workflow.infra.WorkflowMapper;
import com.hify.workflow.infra.WorkflowTemplateMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTemplateServiceImpl implements WorkflowTemplateService {

    private static final String TYPE_MODEL = "MODEL";
    private static final String TYPE_KNOWLEDGE_BASE = "KNOWLEDGE_BASE";
    private static final String TYPE_TOOL = "TOOL";

    private final WorkflowTemplateMapper templateMapper;
    private final WorkflowMapper workflowMapper;
    private final WorkflowService workflowService;
    private final ModelConfigService modelConfigService;
    private final KnowledgeService knowledgeService;
    private final McpService mcpService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<WorkflowTemplateListItemResp> listPage(WorkflowTemplateQuery query) {
        int pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        int pageSize = query.getSize() <= 0 ? 20 : query.getSize();
        Page<WorkflowTemplatePo> page = PageHelper.toPage(pageNo, pageSize);
        return PageHelper.toPageResult(templateMapper.selectPage(page,
                Wrappers.lambdaQuery(WorkflowTemplatePo.class)
                        .like(StringUtils.hasText(query.getName()), WorkflowTemplatePo::getName, query.getName())
                        .eq(StringUtils.hasText(query.getCategory()), WorkflowTemplatePo::getCategory, query.getCategory())
                        .eq(WorkflowTemplatePo::getEnabled, 1)
                        .orderByDesc(WorkflowTemplatePo::getBuiltin)
                        .orderByDesc(WorkflowTemplatePo::getCreatedAt)), this::toListItemResp);
    }

    @Override
    public WorkflowTemplateDetailResp getDetail(Long id) {
        WorkflowTemplatePo template = findEnabledTemplateOrThrow(id);
        return toDetailResp(template);
    }

    @Override
    @Transactional
    public WorkflowDetailResp createWorkflow(Long templateId, CreateWorkflowFromTemplateReq req) {
        WorkflowTemplatePo template = findEnabledTemplateOrThrow(templateId);
        WorkflowTemplateConfig config = parseTemplateConfig(template.getConfigJson());
        Map<String, Long> bindings = req.getBindings() == null ? Collections.emptyMap() : req.getBindings();
        validateBindings(config.getRequirements(), bindings);

        CreateWorkflowReq createReq = new CreateWorkflowReq();
        createReq.setName(req.getName());
        createReq.setDescription(StringUtils.hasText(req.getDescription())
                ? req.getDescription()
                : config.getWorkflow().getDescription());
        createReq.setEnabled(req.getEnabled() == null ? config.getWorkflow().getEnabled() : req.getEnabled());
        createReq.setStartNodeKey(config.getStartNodeKey());
        createReq.setNodes(resolveNodes(config.getNodes(), bindings));
        createReq.setEdges(config.getEdges() == null ? Collections.emptyList() : config.getEdges());

        WorkflowDetailResp created = workflowService.create(createReq);
        WorkflowPo update = new WorkflowPo();
        update.setId(created.getId());
        update.setTemplateId(templateId);
        workflowMapper.updateById(update);
        log.info("created workflow id={} from template id={}", created.getId(), templateId);
        return workflowService.getDetail(created.getId());
    }

    private WorkflowTemplatePo findEnabledTemplateOrThrow(Long id) {
        WorkflowTemplatePo template = templateMapper.selectById(id);
        if (template == null || template.getEnabled() == null || template.getEnabled() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流模板不存在或已禁用: " + id);
        }
        return template;
    }

    private WorkflowTemplateListItemResp toListItemResp(WorkflowTemplatePo po) {
        WorkflowTemplateListItemResp resp = new WorkflowTemplateListItemResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setCategory(po.getCategory());
        resp.setIcon(po.getIcon());
        resp.setEnabled(po.getEnabled());
        resp.setBuiltin(po.getBuiltin());
        resp.setNodeCount(parseNodeCount(po.getConfigJson()));
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private WorkflowTemplateDetailResp toDetailResp(WorkflowTemplatePo po) {
        WorkflowTemplateConfig config = parseTemplateConfig(po.getConfigJson());
        WorkflowTemplateDetailResp resp = new WorkflowTemplateDetailResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setCategory(po.getCategory());
        resp.setIcon(po.getIcon());
        resp.setEnabled(po.getEnabled());
        resp.setBuiltin(po.getBuiltin());
        resp.setConfigJson(readConfigJson(po.getConfigJson()));
        resp.setRequirements(config.getRequirements());
        resp.setNodeCount(config.getNodes() == null ? 0 : config.getNodes().size());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private int parseNodeCount(String configJson) {
        try {
            WorkflowTemplateConfig config = parseTemplateConfig(configJson);
            return config.getNodes() == null ? 0 : config.getNodes().size();
        } catch (BizException e) {
            log.warn("workflow template config invalid when counting nodes: {}", e.getMessage());
            return 0;
        }
    }

    private JsonNode readConfigJson(String configJson) {
        try {
            return objectMapper.readTree(configJson);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "模板 JSON 不合法", e);
        }
    }

    private WorkflowTemplateConfig parseTemplateConfig(String configJson) {
        try {
            JsonNode root = objectMapper.readTree(configJson);
            WorkflowTemplateConfig config = new WorkflowTemplateConfig();
            JsonNode workflowNode = root.path("workflow");
            WorkflowTemplateMeta workflow = new WorkflowTemplateMeta();
            workflow.setName(workflowNode.path("name").asText(""));
            workflow.setDescription(workflowNode.path("description").asText(""));
            workflow.setEnabled("DRAFT".equalsIgnoreCase(workflowNode.path("status").asText())
                    ? 0
                    : workflowNode.path("enabled").asInt(1));
            config.setWorkflow(workflow);
            config.setStartNodeKey(root.path("startNodeKey").asText("start"));
            JsonNode nodesNode = root.path("nodes");
            JsonNode edgesNode = root.path("edges");
            config.setNodes(nodesNode.isArray()
                    ? objectMapper.convertValue(nodesNode, new TypeReference<>() {
                    })
                    : Collections.emptyList());
            config.setEdges(edgesNode.isArray()
                    ? objectMapper.convertValue(edgesNode, new TypeReference<>() {
                    })
                    : Collections.emptyList());
            config.setRequirements(parseRequirements(root.path("requirements")));
            return config;
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "模板 JSON 结构不正确", e);
        }
    }

    private List<WorkflowTemplateRequirementResp> parseRequirements(JsonNode requirementsNode) {
        if (requirementsNode == null || requirementsNode.isMissingNode() || requirementsNode.isNull()) {
            return Collections.emptyList();
        }
        List<WorkflowTemplateRequirementResp> result = new java.util.ArrayList<>();
        appendRequirements(result, requirementsNode.path("models"), TYPE_MODEL);
        appendRequirements(result, requirementsNode.path("knowledgeBases"), TYPE_KNOWLEDGE_BASE);
        appendRequirements(result, requirementsNode.path("tools"), TYPE_TOOL);
        return result;
    }

    private void appendRequirements(List<WorkflowTemplateRequirementResp> result, JsonNode nodes, String type) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            WorkflowTemplateRequirementResp requirement = new WorkflowTemplateRequirementResp();
            requirement.setKey(node.path("key").asText());
            requirement.setType(type);
            requirement.setLabel(node.path("label").asText(requirement.getKey()));
            requirement.setRequired(node.path("required").asBoolean(false));
            if (StringUtils.hasText(requirement.getKey())) {
                result.add(requirement);
            }
        }
    }

    private void validateBindings(List<WorkflowTemplateRequirementResp> requirements, Map<String, Long> bindings) {
        Set<Long> toolIds = new HashSet<>();
        for (WorkflowTemplateRequirementResp requirement : requirements) {
            Long id = bindings.get(requirement.getKey());
            if (Boolean.TRUE.equals(requirement.getRequired()) && id == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "缺少模板资源绑定: " + requirement.getLabel());
            }
            if (id == null) {
                continue;
            }
            String type = requirement.getType().toUpperCase(Locale.ROOT);
            if (TYPE_MODEL.equals(type)) {
                ModelConfigResp model = modelConfigService.getById(id);
                if (model == null || model.getEnabled() == null || model.getEnabled() != 1) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "模型配置不存在或已禁用: " + id);
                }
            } else if (TYPE_KNOWLEDGE_BASE.equals(type)) {
                KnowledgeBaseResp knowledgeBase = knowledgeService.getKnowledgeBase(id);
                if (knowledgeBase == null || knowledgeBase.getEnabled() == null || knowledgeBase.getEnabled() != 1) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "知识库不存在或已禁用: " + id);
                }
            } else if (TYPE_TOOL.equals(type)) {
                toolIds.add(id);
            }
        }
        if (!toolIds.isEmpty()) {
            mcpService.validateEnabledToolIds(toolIds.stream().toList());
        }
    }

    private List<WorkflowNodeDto> resolveNodes(List<WorkflowNodeDto> nodes, Map<String, Long> bindings) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return nodes.stream().map(node -> {
            WorkflowNodeDto resolved = new WorkflowNodeDto();
            resolved.setNodeKey(node.getNodeKey());
            resolved.setNodeType(node.getNodeType());
            resolved.setName(node.getName());
            resolved.setPositionX(node.getPositionX());
            resolved.setPositionY(node.getPositionY());
            resolved.setConfig(resolveConfig(node.getConfig(), bindings));
            return resolved;
        }).toList();
    }

    private JsonNode resolveConfig(JsonNode source, Map<String, Long> bindings) {
        ObjectNode target = objectMapper.createObjectNode();
        JsonNode safeSource = source == null || source.isNull() ? objectMapper.createObjectNode() : source;
        safeSource.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();
            if ("modelConfigRef".equals(fieldName)) {
                target.put("modelConfigId", resolveBindingRef(value, bindings, fieldName));
            } else if ("knowledgeBaseRef".equals(fieldName)) {
                target.put("knowledgeBaseId", resolveBindingRef(value, bindings, fieldName));
            } else if ("toolRef".equals(fieldName)) {
                target.put("toolId", resolveBindingRef(value, bindings, fieldName));
            } else {
                target.set(fieldName, value);
            }
        });
        ensureNoUnresolvedResourceRefs(target);
        return target;
    }

    private Long resolveBindingRef(JsonNode value, Map<String, Long> bindings, String fieldName) {
        String raw = value == null || value.isNull() ? "" : value.asText();
        String key = raw.replaceAll("^\\{\\{", "").replaceAll("}}$", "");
        Long binding = bindings.get(key);
        if (binding == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板资源未绑定: " + fieldName + "=" + raw);
        }
        return binding;
    }

    private void ensureNoUnresolvedResourceRefs(ObjectNode config) {
        if (config.has("modelConfigRef") || config.has("knowledgeBaseRef") || config.has("toolRef")) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "模板资源占位符未解析完成");
        }
    }

    @Data
    private static class WorkflowTemplateConfig {
        private WorkflowTemplateMeta workflow = new WorkflowTemplateMeta();
        private String startNodeKey;
        private List<WorkflowNodeDto> nodes = Collections.emptyList();
        private List<WorkflowEdgeDto> edges = Collections.emptyList();
        private List<WorkflowTemplateRequirementResp> requirements = Collections.emptyList();
    }

    @Data
    private static class WorkflowTemplateMeta {
        private String name;
        private String description;
        private Integer enabled = 1;
    }
}
