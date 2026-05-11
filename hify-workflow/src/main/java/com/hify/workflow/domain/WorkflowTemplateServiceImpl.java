package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import com.hify.workflow.api.CreateTemplateFromWorkflowReq;
import com.hify.workflow.api.CreateWorkflowReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowEdgeDto;
import com.hify.workflow.api.WorkflowNodeDto;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowTemplateDetailResp;
import com.hify.workflow.api.WorkflowTemplateExportResp;
import com.hify.workflow.api.WorkflowTemplateListItemResp;
import com.hify.workflow.api.WorkflowTemplateQuery;
import com.hify.workflow.api.WorkflowTemplateRequirementResp;
import com.hify.workflow.api.WorkflowTemplateService;
import com.hify.workflow.infra.WorkflowMapper;
import com.hify.workflow.infra.WorkflowTemplateMapper;
import com.hify.workflow.infra.WorkflowTemplateUsageMapper;
import com.hify.workflow.infra.WorkflowTemplateVersionMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private final WorkflowTemplateVersionMapper versionMapper;
    private final WorkflowTemplateUsageMapper usageMapper;
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
                        .ne(WorkflowTemplatePo::getStatus, "ARCHIVED")
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
        WorkflowTemplateVersionPo version = loadCurrentVersion(template);
        WorkflowTemplateConfig config = parseTemplateConfig(snapshotJson(template, version));
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
        update.setSourceTemplateVersionId(version == null ? null : version.getId());
        workflowMapper.updateById(update);
        recordUsage(templateId, version == null ? null : version.getId(), created.getId(), "USE");
        templateMapper.update(null, Wrappers.lambdaUpdate(WorkflowTemplatePo.class)
                .eq(WorkflowTemplatePo::getId, templateId)
                .setSql("usage_count = usage_count + 1")
                .set(WorkflowTemplatePo::getLastUsedAt, LocalDateTime.now()));
        log.info("created workflow id={} from template id={}", created.getId(), templateId);
        return workflowService.getDetail(created.getId());
    }

    @Override
    @Transactional
    public WorkflowTemplateDetailResp createFromWorkflow(CreateTemplateFromWorkflowReq req) {
        WorkflowDetailResp workflow = workflowService.getDetail(req.getWorkflowId());
        if (workflow == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在: " + req.getWorkflowId());
        }
        JsonNode snapshot = buildTemplateSnapshot(req, workflow);
        WorkflowTemplateConfig config = parseTemplateConfig(snapshot.toString());
        LocalDateTime now = LocalDateTime.now();
        boolean publish = Boolean.TRUE.equals(req.getPublish());

        WorkflowTemplatePo template = new WorkflowTemplatePo();
        template.setName(req.getName().trim());
        template.setDescription(req.getDescription() == null ? "" : req.getDescription().trim());
        template.setCategory(StringUtils.hasText(req.getCategory()) ? req.getCategory().trim() : "通用");
        template.setIcon(StringUtils.hasText(req.getIcon()) ? req.getIcon().trim() : "workflow");
        template.setConfigJson(snapshot.toString());
        template.setEnabled(1);
        template.setBuiltin(0);
        template.setStatus(publish ? "PUBLISHED" : "DRAFT");
        template.setLatestVersionNo(1);
        template.setTagsJson(toJson(req.getTags() == null ? List.of() : req.getTags()));
        template.setNodeCount(config.getNodes().size());
        template.setNodeTypesJson(toJson(nodeTypes(config.getNodes())));
        template.setRequirementCount(config.getRequirements().size());
        template.setUsageCount(0);
        template.setCreatedFromWorkflowId(req.getWorkflowId());
        template.setPublishedAt(publish ? now : null);
        templateMapper.insert(template);

        WorkflowTemplateVersionPo version = new WorkflowTemplateVersionPo();
        version.setTemplateId(template.getId());
        version.setVersionNo(1);
        version.setSnapshotJson(snapshot.toString());
        version.setRequirementsJson(toRequirementsJson(snapshot.path("requirements")));
        version.setNodeCount(config.getNodes().size());
        version.setNodeTypesJson(toJson(nodeTypes(config.getNodes())));
        version.setChecksum(sha256(snapshot.toString()));
        version.setChangelog(req.getChangelog() == null ? "" : req.getChangelog());
        version.setValidationStatus("PASSED");
        version.setValidationErrorsJson("[]");
        version.setPublishedAt(publish ? now : null);
        versionMapper.insert(version);

        template.setCurrentVersionId(version.getId());
        templateMapper.updateById(template);
        log.info("created workflow template id={} from workflow id={}", template.getId(), req.getWorkflowId());
        return toDetailResp(template);
    }

    @Override
    public WorkflowTemplateExportResp exportTemplate(Long templateId, Long versionId) {
        WorkflowTemplatePo template = findEnabledTemplateOrThrow(templateId);
        WorkflowTemplateVersionPo version = versionId == null ? loadCurrentVersion(template) : versionMapper.selectById(versionId);
        if (version == null || !templateId.equals(version.getTemplateId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "模板版本不存在: " + versionId);
        }
        WorkflowTemplateExportResp resp = new WorkflowTemplateExportResp();
        resp.setFilename(safeFilename(template.getName()) + "-v" + version.getVersionNo() + ".json");
        resp.setTemplateJson(readConfigJson(version.getSnapshotJson()));
        recordUsage(templateId, version.getId(), null, "EXPORT");
        return resp;
    }

    private WorkflowTemplatePo findEnabledTemplateOrThrow(Long id) {
        WorkflowTemplatePo template = templateMapper.selectById(id);
        if (template == null || template.getEnabled() == null || template.getEnabled() != 1
                || "ARCHIVED".equals(template.getStatus())) {
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
        WorkflowTemplateVersionPo version = loadCurrentVersion(po);
        WorkflowTemplateConfig config = parseTemplateConfig(snapshotJson(po, version));
        resp.setNodeCount(effectiveNodeCount(po, version, config));
        resp.setStatus(effectiveStatus(po));
        resp.setCurrentVersionId(po.getCurrentVersionId());
        resp.setLatestVersionNo(po.getLatestVersionNo() == null ? 0 : po.getLatestVersionNo());
        resp.setTags(fromJsonList(po.getTagsJson(), String.class));
        resp.setNodeTypes(effectiveNodeTypes(po, version, config));
        resp.setRequirementCount(po.getRequirementCount() == null ? config.getRequirements().size() : po.getRequirementCount());
        resp.setUsageCount(po.getUsageCount() == null ? 0 : po.getUsageCount());
        resp.setLastUsedAt(po.getLastUsedAt());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private WorkflowTemplateDetailResp toDetailResp(WorkflowTemplatePo po) {
        WorkflowTemplateVersionPo version = loadCurrentVersion(po);
        String snapshotJson = snapshotJson(po, version);
        WorkflowTemplateConfig config = parseTemplateConfig(snapshotJson);
        WorkflowTemplateDetailResp resp = new WorkflowTemplateDetailResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setCategory(po.getCategory());
        resp.setIcon(po.getIcon());
        resp.setEnabled(po.getEnabled());
        resp.setBuiltin(po.getBuiltin());
        resp.setConfigJson(readConfigJson(snapshotJson));
        resp.setRequirements(version == null
                ? config.getRequirements()
                : parseRequirements(readConfigJson(version.getRequirementsJson())));
        resp.setNodeCount(effectiveNodeCount(po, version, config));
        resp.setStatus(effectiveStatus(po));
        resp.setCurrentVersionId(po.getCurrentVersionId());
        resp.setLatestVersionNo(po.getLatestVersionNo() == null ? 0 : po.getLatestVersionNo());
        resp.setTags(fromJsonList(po.getTagsJson(), String.class));
        resp.setNodeTypes(effectiveNodeTypes(po, version, config));
        resp.setRequirementCount(po.getRequirementCount() == null ? config.getRequirements().size() : po.getRequirementCount());
        resp.setUsageCount(po.getUsageCount() == null ? 0 : po.getUsageCount());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private WorkflowTemplateVersionPo loadCurrentVersion(WorkflowTemplatePo template) {
        if (template.getCurrentVersionId() == null) {
            return null;
        }
        WorkflowTemplateVersionPo version = versionMapper.selectById(template.getCurrentVersionId());
        if (version == null) {
            log.warn("workflow template current version missing templateId={} versionId={}",
                    template.getId(), template.getCurrentVersionId());
        }
        return version;
    }

    private String snapshotJson(WorkflowTemplatePo template, WorkflowTemplateVersionPo version) {
        return version == null ? template.getConfigJson() : version.getSnapshotJson();
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

    private int effectiveNodeCount(WorkflowTemplatePo template, WorkflowTemplateVersionPo version,
                                   WorkflowTemplateConfig config) {
        if (template.getNodeCount() != null) {
            return template.getNodeCount();
        }
        if (version != null && version.getNodeCount() != null) {
            return version.getNodeCount();
        }
        return config.getNodes() == null ? 0 : config.getNodes().size();
    }

    private String effectiveStatus(WorkflowTemplatePo template) {
        if (StringUtils.hasText(template.getStatus())) {
            return template.getStatus();
        }
        return template.getEnabled() != null && template.getEnabled() == 1 ? "PUBLISHED" : "DRAFT";
    }

    private List<String> effectiveNodeTypes(WorkflowTemplatePo template, WorkflowTemplateVersionPo version,
                                            WorkflowTemplateConfig config) {
        List<String> fromTemplate = fromJsonList(template.getNodeTypesJson(), String.class);
        if (!fromTemplate.isEmpty()) {
            return fromTemplate;
        }
        if (version != null) {
            List<String> fromVersion = fromJsonList(version.getNodeTypesJson(), String.class);
            if (!fromVersion.isEmpty()) {
                return fromVersion;
            }
        }
        return nodeTypes(config.getNodes());
    }

    private List<String> nodeTypes(List<WorkflowNodeDto> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (WorkflowNodeDto node : nodes) {
            if (StringUtils.hasText(node.getNodeType())) {
                types.add(node.getNodeType());
            }
        }
        return new ArrayList<>(types);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "模板 JSON 序列化失败", e);
        }
    }

    private <T> List<T> fromJsonList(String json, Class<T> itemType) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<T> result = new ArrayList<>();
            for (JsonNode item : node) {
                result.add(objectMapper.convertValue(item, itemType));
            }
            return result;
        } catch (IllegalArgumentException | JsonProcessingException e) {
            log.warn("workflow template json list invalid: {}", e.getMessage());
            return List.of();
        }
    }

    private String toRequirementsJson(JsonNode requirementsNode) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("models", requirementsNode != null && requirementsNode.path("models").isArray()
                ? requirementsNode.path("models")
                : objectMapper.createArrayNode());
        root.set("knowledgeBases", requirementsNode != null && requirementsNode.path("knowledgeBases").isArray()
                ? requirementsNode.path("knowledgeBases")
                : objectMapper.createArrayNode());
        root.set("tools", requirementsNode != null && requirementsNode.path("tools").isArray()
                ? requirementsNode.path("tools")
                : objectMapper.createArrayNode());
        return root.toString();
    }

    private JsonNode readConfigJson(String configJson) {
        try {
            return objectMapper.readTree(configJson);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "模板 JSON 不合法", e);
        }
    }

    private JsonNode buildTemplateSnapshot(CreateTemplateFromWorkflowReq req, WorkflowDetailResp workflow) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        ObjectNode templateNode = root.putObject("template");
        templateNode.put("name", req.getName());
        templateNode.put("description", req.getDescription() == null ? "" : req.getDescription());
        templateNode.put("category", StringUtils.hasText(req.getCategory()) ? req.getCategory() : "通用");
        templateNode.set("tags", objectMapper.valueToTree(req.getTags() == null ? List.of() : req.getTags()));
        templateNode.put("icon", StringUtils.hasText(req.getIcon()) ? req.getIcon() : "workflow");
        ObjectNode workflowNode = root.putObject("workflow");
        workflowNode.put("name", req.getName());
        workflowNode.put("description", req.getDescription() == null ? "" : req.getDescription());
        workflowNode.put("enabled", 0);
        root.put("startNodeKey", workflow.getStartNodeKey());
        ResourceRequirements requirements = new ResourceRequirements();
        root.set("nodes", objectMapper.valueToTree(toTemplateNodes(workflow.getNodes(), requirements)));
        root.set("edges", objectMapper.valueToTree(workflow.getEdges() == null ? List.of() : workflow.getEdges()));
        root.set("requirements", requirements.toJson(objectMapper));
        return root;
    }

    private List<WorkflowNodeDto> toTemplateNodes(List<WorkflowNodeDto> nodes, ResourceRequirements requirements) {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream().map(node -> {
            WorkflowNodeDto copy = new WorkflowNodeDto();
            copy.setNodeKey(node.getNodeKey());
            copy.setNodeType(node.getNodeType());
            copy.setName(node.getName());
            copy.setPositionX(node.getPositionX());
            copy.setPositionY(node.getPositionY());
            copy.setConfig(toTemplateConfig(node, requirements));
            return copy;
        }).toList();
    }

    private JsonNode toTemplateConfig(WorkflowNodeDto node, ResourceRequirements requirements) {
        ObjectNode target = objectMapper.createObjectNode();
        JsonNode source = node.getConfig() == null || node.getConfig().isNull()
                ? objectMapper.createObjectNode()
                : node.getConfig();
        source.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();
            if ("modelConfigId".equals(fieldName)) {
                String key = "model.chat";
                requirements.addModel(key, "聊天模型");
                target.put("modelConfigRef", "{{" + key + "}}");
            } else if ("knowledgeBaseId".equals(fieldName)) {
                String key = "knowledge.base";
                requirements.addKnowledgeBase(key, "业务知识库");
                target.put("knowledgeBaseRef", "{{" + key + "}}");
            } else if ("toolId".equals(fieldName)) {
                String key = "tool." + node.getNodeKey();
                requirements.addTool(key, node.getName() + "工具");
                target.put("toolRef", "{{" + key + "}}");
            } else {
                target.set(fieldName, sanitizeNodeConfigValue(value));
            }
        });
        return target;
    }

    private void recordUsage(Long templateId, Long versionId, Long workflowId, String actionType) {
        if (versionId == null) {
            return;
        }
        WorkflowTemplateUsagePo usage = new WorkflowTemplateUsagePo();
        usage.setTemplateId(templateId);
        usage.setVersionId(versionId);
        usage.setWorkflowId(workflowId);
        usage.setActionType(actionType);
        usageMapper.insert(usage);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static String safeFilename(String name) {
        String safe = StringUtils.hasText(name) ? name.trim().replaceAll("[\\\\/:*?\"<>|]+", "_") : "workflow-template";
        return StringUtils.hasText(safe) ? safe : "workflow-template";
    }

    private JsonNode sanitizeNodeConfigValue(JsonNode value) {
        if (!value.isObject()) {
            return value;
        }
        ObjectNode source = (ObjectNode) value;
        ObjectNode target = objectMapper.createObjectNode();
        source.fields().forEachRemaining(entry -> {
            String lower = entry.getKey().toLowerCase(Locale.ROOT);
            if (lower.contains("authorization") || lower.contains("token")
                    || lower.contains("apikey") || lower.contains("api_key")
                    || lower.contains("cookie") || lower.contains("secret")) {
                target.put(entry.getKey(), "");
            } else {
                target.set(entry.getKey(), sanitizeNodeConfigValue(entry.getValue()));
            }
        });
        return target;
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

    private static class ResourceRequirements {
        private final LinkedHashSet<ResourceRequirement> models = new LinkedHashSet<>();
        private final LinkedHashSet<ResourceRequirement> knowledgeBases = new LinkedHashSet<>();
        private final LinkedHashSet<ResourceRequirement> tools = new LinkedHashSet<>();

        void addModel(String key, String label) {
            models.add(new ResourceRequirement(key, "CHAT", label, true));
        }

        void addKnowledgeBase(String key, String label) {
            knowledgeBases.add(new ResourceRequirement(key, null, label, true));
        }

        void addTool(String key, String label) {
            tools.add(new ResourceRequirement(key, null, label, true));
        }

        JsonNode toJson(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.set("models", toArray(objectMapper, models));
            root.set("knowledgeBases", toArray(objectMapper, knowledgeBases));
            root.set("tools", toArray(objectMapper, tools));
            return root;
        }

        private ArrayNode toArray(ObjectMapper objectMapper, LinkedHashSet<ResourceRequirement> requirements) {
            ArrayNode array = objectMapper.createArrayNode();
            for (ResourceRequirement requirement : requirements) {
                ObjectNode node = array.addObject();
                node.put("key", requirement.key());
                if (StringUtils.hasText(requirement.type())) {
                    node.put("type", requirement.type());
                }
                node.put("label", requirement.label());
                node.put("required", requirement.required());
            }
            return array;
        }
    }

    private record ResourceRequirement(String key, String type, String label, boolean required) {
    }
}
