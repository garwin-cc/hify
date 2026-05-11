package com.hify.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.mcp.api.McpService;
import com.hify.model.api.ModelConfigService;
import com.hify.workflow.api.CreateTemplateFromWorkflowReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowEdgeDto;
import com.hify.workflow.api.WorkflowNodeDto;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowTemplateDetailResp;
import com.hify.workflow.api.WorkflowTemplateExportResp;
import com.hify.workflow.infra.WorkflowMapper;
import com.hify.workflow.infra.WorkflowTemplateMapper;
import com.hify.workflow.infra.WorkflowTemplateUsageMapper;
import com.hify.workflow.infra.WorkflowTemplateVersionMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTemplateServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getDetailReadsCurrentVersionSnapshotMetadata() throws Exception {
        WorkflowTemplatePo template = templatePo(1L);
        template.setCurrentVersionId(10L);
        template.setLatestVersionNo(2);
        template.setStatus("PUBLISHED");

        WorkflowTemplateVersionPo version = new WorkflowTemplateVersionPo();
        version.setId(10L);
        version.setTemplateId(1L);
        version.setVersionNo(2);
        version.setSnapshotJson(templateJson());
        version.setRequirementsJson("{\"models\":[{\"key\":\"model.chat\",\"type\":\"CHAT\",\"label\":\"聊天模型\",\"required\":true}],\"knowledgeBases\":[],\"tools\":[]}");
        version.setNodeCount(2);
        version.setNodeTypesJson("[\"START\",\"LLM\"]");

        WorkflowTemplateServiceImpl service = newService(
                mapper(WorkflowTemplateMapper.class, method -> "selectById".equals(method) ? args -> template : null),
                mapper(WorkflowTemplateVersionMapper.class, method -> "selectById".equals(method) ? args -> version : null),
                mapper(WorkflowTemplateUsageMapper.class, method -> null),
                mapper(WorkflowService.class, method -> null));

        WorkflowTemplateDetailResp detail = service.getDetail(1L);

        assertThat(detail.getCurrentVersionId()).isEqualTo(10L);
        assertThat(detail.getLatestVersionNo()).isEqualTo(2);
        assertThat(detail.getStatus()).isEqualTo("PUBLISHED");
        assertThat(detail.getNodeTypes()).containsExactly("START", "LLM");
        assertThat(detail.getRequirements()).hasSize(1);
        assertThat(detail.getConfigJson().path("schemaVersion").asText()).isEqualTo("1.0");
    }

    @Test
    void createFromWorkflowCreatesDraftTemplateAndVersionWithResourcePlaceholders() throws Exception {
        List<WorkflowTemplatePo> insertedTemplates = new ArrayList<>();
        List<WorkflowTemplateVersionPo> insertedVersions = new ArrayList<>();
        WorkflowService workflowService = mapper(WorkflowService.class,
                method -> "getDetail".equals(method) ? args -> workflowDetail() : null);

        WorkflowTemplateServiceImpl service = newService(
                mapper(WorkflowTemplateMapper.class, method -> {
                    if ("insert".equals(method)) {
                        return args -> {
                            WorkflowTemplatePo po = (WorkflowTemplatePo) args[0];
                            po.setId(100L);
                            insertedTemplates.add(po);
                            return 1;
                        };
                    }
                    if ("updateById".equals(method)) {
                        return args -> 1;
                    }
                    if ("selectById".equals(method)) {
                        return args -> insertedTemplates.get(0);
                    }
                    return null;
                }),
                mapper(WorkflowTemplateVersionMapper.class, method -> {
                    if ("insert".equals(method)) {
                        return args -> {
                            WorkflowTemplateVersionPo po = (WorkflowTemplateVersionPo) args[0];
                            po.setId(200L);
                            insertedVersions.add(po);
                            return 1;
                        };
                    }
                    if ("selectById".equals(method)) {
                        return args -> insertedVersions.get(0);
                    }
                    return null;
                }),
                mapper(WorkflowTemplateUsageMapper.class, method -> null),
                workflowService);

        CreateTemplateFromWorkflowReq req = new CreateTemplateFromWorkflowReq();
        req.setWorkflowId(7L);
        req.setName("客服模板");
        req.setDescription("从客服工作流沉淀");
        req.setCategory("客服");
        req.setTags(List.of("客服", "LLM"));
        req.setPublish(false);

        WorkflowTemplateDetailResp detail = service.createFromWorkflow(req);

        assertThat(detail.getId()).isEqualTo(100L);
        assertThat(insertedTemplates.get(0).getStatus()).isEqualTo("DRAFT");
        assertThat(insertedTemplates.get(0).getLatestVersionNo()).isEqualTo(1);
        JsonNode snapshot = objectMapper.readTree(insertedVersions.get(0).getSnapshotJson());
        JsonNode llmConfig = snapshot.path("nodes").get(1).path("config");
        assertThat(llmConfig.path("modelConfigRef").asText()).isEqualTo("{{model.chat}}");
        assertThat(llmConfig.has("modelConfigId")).isFalse();
        assertThat(snapshot.path("requirements").path("models").get(0).path("key").asText()).isEqualTo("model.chat");
    }

    @Test
    void exportTemplateReturnsVersionSnapshotAndWritesUsage() {
        WorkflowTemplatePo template = templatePo(1L);
        template.setCurrentVersionId(10L);
        WorkflowTemplateVersionPo version = new WorkflowTemplateVersionPo();
        version.setId(10L);
        version.setTemplateId(1L);
        version.setVersionNo(1);
        version.setSnapshotJson(templateJson());
        List<WorkflowTemplateUsagePo> usages = new ArrayList<>();

        WorkflowTemplateServiceImpl service = newService(
                mapper(WorkflowTemplateMapper.class, method -> "selectById".equals(method) ? args -> template : null),
                mapper(WorkflowTemplateVersionMapper.class, method -> "selectById".equals(method) ? args -> version : null),
                mapper(WorkflowTemplateUsageMapper.class, method -> {
                    if ("insert".equals(method)) {
                        return args -> {
                            usages.add((WorkflowTemplateUsagePo) args[0]);
                            return 1;
                        };
                    }
                    return null;
                }),
                mapper(WorkflowService.class, method -> null));

        WorkflowTemplateExportResp resp = service.exportTemplate(1L, 10L);

        assertThat(resp.getFilename()).isEqualTo("客服模板-v1.json");
        assertThat(resp.getTemplateJson().path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).getActionType()).isEqualTo("EXPORT");
    }

    private WorkflowTemplateServiceImpl newService(WorkflowTemplateMapper templateMapper,
                                                   WorkflowTemplateVersionMapper versionMapper,
                                                   WorkflowTemplateUsageMapper usageMapper,
                                                   WorkflowService workflowService) {
        return new WorkflowTemplateServiceImpl(
                templateMapper,
                mapper(WorkflowMapper.class, method -> null),
                versionMapper,
                usageMapper,
                workflowService,
                mapper(ModelConfigService.class, method -> null),
                mapper(KnowledgeService.class, method -> null),
                mapper(McpService.class, method -> null),
                objectMapper);
    }

    private WorkflowTemplatePo templatePo(Long id) {
        WorkflowTemplatePo po = new WorkflowTemplatePo();
        po.setId(id);
        po.setName("客服模板");
        po.setDescription("客服场景");
        po.setCategory("客服");
        po.setIcon("service");
        po.setConfigJson(templateJson());
        po.setEnabled(1);
        po.setBuiltin(0);
        return po;
    }

    private String templateJson() {
        return "{\"schemaVersion\":\"1.0\",\"workflow\":{\"name\":\"客服模板\",\"description\":\"客服场景\",\"enabled\":0},\"startNodeKey\":\"start\",\"nodes\":[{\"nodeKey\":\"start\",\"nodeType\":\"START\",\"name\":\"开始\",\"config\":{},\"positionX\":80,\"positionY\":100},{\"nodeKey\":\"answer\",\"nodeType\":\"LLM\",\"name\":\"回答\",\"config\":{\"modelConfigRef\":\"{{model.chat}}\",\"prompt\":\"{{start.userMessage}}\",\"outputVariable\":\"answer\"},\"positionX\":320,\"positionY\":100}],\"edges\":[{\"sourceNodeKey\":\"start\",\"targetNodeKey\":\"answer\",\"edgeType\":\"DEFAULT\",\"sortOrder\":0}],\"requirements\":{\"models\":[{\"key\":\"model.chat\",\"type\":\"CHAT\",\"label\":\"聊天模型\",\"required\":true}],\"knowledgeBases\":[],\"tools\":[]}}";
    }

    private WorkflowDetailResp workflowDetail() {
        WorkflowDetailResp detail = new WorkflowDetailResp();
        detail.setId(7L);
        detail.setName("客服工作流");
        detail.setDescription("回答客户问题");
        detail.setEnabled(0);
        detail.setStartNodeKey("start");

        WorkflowNodeDto start = new WorkflowNodeDto();
        start.setNodeKey("start");
        start.setNodeType("START");
        start.setName("开始");
        start.setConfig(objectMapper.createObjectNode());
        start.setPositionX(80);
        start.setPositionY(100);

        WorkflowNodeDto answer = new WorkflowNodeDto();
        answer.setNodeKey("answer");
        answer.setNodeType("LLM");
        answer.setName("回答");
        try {
            answer.setConfig(objectMapper.readTree("{\"modelConfigId\":12,\"prompt\":\"{{start.userMessage}}\",\"outputVariable\":\"answer\"}"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        answer.setPositionX(320);
        answer.setPositionY(100);

        WorkflowEdgeDto edge = new WorkflowEdgeDto();
        edge.setSourceNodeKey("start");
        edge.setTargetNodeKey("answer");
        edge.setEdgeType("DEFAULT");
        edge.setSortOrder(0);

        detail.setNodes(List.of(start, answer));
        detail.setEdges(List.of(edge));
        return detail;
    }

    private static <T> T mapper(Class<T> type, Function<String, Invocation> behavior) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    Invocation invocation = behavior.apply(method.getName());
                    if (invocation != null) {
                        return invocation.invoke(args == null ? new Object[0] : args);
                    }
                    if (method.getReturnType().equals(int.class) || method.getReturnType().equals(Integer.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class)) {
                        return false;
                    }
                    return null;
                }));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Object[] args);
    }
}
