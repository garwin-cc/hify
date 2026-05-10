package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.log.TraceContext;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.workflow.api.CreateWorkflowReq;
import com.hify.workflow.api.SubmitWorkflowReviewReq;
import com.hify.workflow.api.UpdateWorkflowReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowEdgeDto;
import com.hify.workflow.api.WorkflowListItemResp;
import com.hify.workflow.api.WorkflowNodeDebugReq;
import com.hify.workflow.api.WorkflowNodeDebugResp;
import com.hify.workflow.api.WorkflowNodeRunResp;
import com.hify.workflow.api.WorkflowNodeDto;
import com.hify.workflow.api.WorkflowQuery;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowReviewTaskResp;
import com.hify.workflow.api.WorkflowVersionResp;
import com.hify.workflow.domain.config.NodeConfigParser;
import com.hify.workflow.engine.WorkflowEngine;
import com.hify.workflow.infra.WorkflowEdgeMapper;
import com.hify.workflow.infra.WorkflowMapper;
import com.hify.workflow.infra.WorkflowNodeMapper;
import com.hify.workflow.infra.WorkflowNodeRunMapper;
import com.hify.workflow.infra.WorkflowRunMapper;
import com.hify.workflow.infra.WorkflowVersionMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowNodeRunMapper workflowNodeRunMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final NodeConfigParser nodeConfigParser;
    private final WorkflowEngine workflowEngine;
    private final WorkflowRunEventService workflowRunEventService;
    private final WorkflowReviewService workflowReviewService;
    private final ObjectMapper objectMapper;
    @Resource(name = "llmExecutor")
    private ThreadPoolExecutor llmExecutor;

    @Override
    @Transactional
    public WorkflowDetailResp create(CreateWorkflowReq req) {
        validateDefinition(req);
        WorkflowPo workflow = new WorkflowPo();
        workflow.setName(req.getName());
        workflow.setDescription(req.getDescription() == null ? "" : req.getDescription());
        workflow.setEnabled(req.getEnabled() == null ? 1 : normalizeEnabled(req.getEnabled()));
        workflow.setStartNodeKey(req.getStartNodeKey());
        workflowMapper.insert(workflow);

        insertNodesAndEdges(workflow.getId(), req.getNodes(), req.getEdges());
        saveVersionSnapshot(workflow.getId(), "创建工作流");
        log.info("created workflow id={} name={}", workflow.getId(), workflow.getName());
        return getDetail(workflow.getId());
    }

    @Override
    public PageResult<WorkflowListItemResp> listPage(WorkflowQuery query) {
        int pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        int pageSize = query.getSize() <= 0 ? 20 : query.getSize();
        Page<WorkflowPo> page = PageHelper.toPage(pageNo, pageSize);
        return PageHelper.toPageResult(workflowMapper.selectPage(page,
                Wrappers.lambdaQuery(WorkflowPo.class)
                        .like(StringUtils.hasText(query.getName()), WorkflowPo::getName, query.getName())
                        .eq(query.getEnabled() != null, WorkflowPo::getEnabled, query.getEnabled())
                        .orderByDesc(WorkflowPo::getCreatedAt)), this::toListItemResp);
    }

    @Override
    public WorkflowDetailResp getDetail(Long id) {
        WorkflowPo workflow = findWorkflowOrThrow(id);
        List<WorkflowNodePo> nodes = nodeMapper.selectList(Wrappers.lambdaQuery(WorkflowNodePo.class)
                .eq(WorkflowNodePo::getWorkflowId, id)
                .orderByAsc(WorkflowNodePo::getId));
        List<WorkflowEdgePo> edges = edgeMapper.selectList(Wrappers.lambdaQuery(WorkflowEdgePo.class)
                .eq(WorkflowEdgePo::getWorkflowId, id)
                .orderByAsc(WorkflowEdgePo::getSortOrder)
                .orderByAsc(WorkflowEdgePo::getId));
        return toDetailResp(workflow, nodes, edges);
    }

    @Override
    @Transactional
    public WorkflowDetailResp update(Long id, UpdateWorkflowReq req) {
        validateDefinition(req);
        WorkflowPo workflow = findWorkflowOrThrow(id);
        workflow.setName(req.getName());
        workflow.setDescription(req.getDescription() == null ? "" : req.getDescription());
        workflow.setEnabled(req.getEnabled() == null ? 1 : normalizeEnabled(req.getEnabled()));
        workflow.setStartNodeKey(req.getStartNodeKey());
        workflowMapper.updateById(workflow);

        nodeMapper.delete(Wrappers.lambdaQuery(WorkflowNodePo.class)
                .eq(WorkflowNodePo::getWorkflowId, id));
        edgeMapper.delete(Wrappers.lambdaQuery(WorkflowEdgePo.class)
                .eq(WorkflowEdgePo::getWorkflowId, id));
        insertNodesAndEdges(id, req.getNodes(), req.getEdges());
        saveVersionSnapshot(id, "更新工作流");
        log.info("updated workflow id={}", id);
        return getDetail(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        findWorkflowOrThrow(id);
        workflowMapper.deleteById(id);
        nodeMapper.delete(Wrappers.lambdaQuery(WorkflowNodePo.class)
                .eq(WorkflowNodePo::getWorkflowId, id));
        edgeMapper.delete(Wrappers.lambdaQuery(WorkflowEdgePo.class)
                .eq(WorkflowEdgePo::getWorkflowId, id));
        log.info("deleted workflow id={}", id);
    }

    @Override
    public WorkflowRunResp run(Long id, WorkflowRunReq req) {
        findWorkflowOrThrow(id);
        workflowEngine.execute(id, req.getUserMessage());
        return getLatestRun(id);
    }

    @Override
    public WorkflowRunResp startAsyncRun(Long id, WorkflowRunReq req) {
        findWorkflowOrThrow(id);
        WorkflowRunPo run = workflowEngine.createWorkflowRun(
                id,
                req.getUserMessage(),
                "ASYNC",
                LocalDateTime.now().plusSeconds(300));
        try {
            llmExecutor.execute(TraceContext.wrap(() -> {
                try {
                    workflowEngine.executeExistingRun(run.getId(), id, req.getUserMessage());
                } catch (Exception e) {
                    log.warn("async workflow run failed runId={} workflowId={}: {}",
                            run.getId(), id, e.getMessage());
                }
            }));
        } catch (Exception e) {
            markRunFailed(run, e);
        }
        return getRunDetail(run.getId());
    }

    @Override
    public WorkflowNodeDebugResp debugNode(Long workflowId, String nodeKey, WorkflowNodeDebugReq req) {
        findWorkflowOrThrow(workflowId);
        long startedAt = System.currentTimeMillis();
        WorkflowNodeDebugResp resp = new WorkflowNodeDebugResp();
        try {
            Map<String, Object> outputs = workflowEngine.debugNode(
                    workflowId,
                    nodeKey,
                    req == null ? "" : req.getUserMessage(),
                    req == null ? Collections.emptyMap() : req.getVariables());
            resp.setStatus("SUCCESS");
            resp.setOutputs(outputs);
        } catch (Exception e) {
            resp.setStatus("FAILED");
            resp.setError(shortError(e));
        }
        resp.setElapsedMs(elapsed(startedAt));
        return resp;
    }

    @Override
    public List<WorkflowVersionResp> listVersions(Long workflowId) {
        findWorkflowOrThrow(workflowId);
        return workflowVersionMapper.selectList(Wrappers.lambdaQuery(WorkflowVersionPo.class)
                        .eq(WorkflowVersionPo::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowVersionPo::getVersionNo))
                .stream()
                .map(this::toVersionResp)
                .toList();
    }

    @Override
    public WorkflowVersionResp getVersion(Long workflowId, Integer versionNo) {
        findWorkflowOrThrow(workflowId);
        WorkflowVersionPo version = findVersionOrThrow(workflowId, versionNo);
        return toVersionResp(version);
    }

    @Override
    @Transactional
    public WorkflowDetailResp restoreVersion(Long workflowId, Integer versionNo) {
        findWorkflowOrThrow(workflowId);
        WorkflowVersionPo version = findVersionOrThrow(workflowId, versionNo);
        WorkflowDetailResp snapshot = parseSnapshot(version.getSnapshotJson());
        UpdateWorkflowReq req = new UpdateWorkflowReq();
        req.setName(snapshot.getName());
        req.setDescription(snapshot.getDescription());
        req.setEnabled(snapshot.getEnabled());
        req.setStartNodeKey(snapshot.getStartNodeKey());
        req.setNodes(snapshot.getNodes());
        req.setEdges(snapshot.getEdges());
        return update(workflowId, req);
    }

    @Override
    public WorkflowRunResp getRunDetail(Long runId) {
        WorkflowRunPo run = workflowRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流执行记录不存在: " + runId);
        }
        List<WorkflowNodeRunPo> nodeRuns = workflowNodeRunMapper.selectList(
                Wrappers.lambdaQuery(WorkflowNodeRunPo.class)
                        .eq(WorkflowNodeRunPo::getWorkflowRunId, run.getId())
                        .orderByAsc(WorkflowNodeRunPo::getId));
        return toRunResp(run, nodeRuns);
    }

    @Override
    public SseEmitter streamRunEvents(Long runId, Integer afterEventSeq) {
        WorkflowRunPo run = workflowRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流执行记录不存在: " + runId);
        }
        return workflowRunEventService.subscribe(runId, afterEventSeq, isTerminalRun(run.getStatus()));
    }

    @Override
    public WorkflowReviewTaskResp getReviewTask(Long runId) {
        WorkflowRunPo run = workflowRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流执行记录不存在: " + runId);
        }
        return workflowReviewService.getWaitingReview(runId);
    }

    @Override
    @Transactional
    public WorkflowRunResp submitReview(Long runId, SubmitWorkflowReviewReq req) {
        WorkflowRunPo run = workflowRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流执行记录不存在: " + runId);
        }
        if (!"WAITING".equals(run.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流当前不在待评审状态");
        }
        WorkflowReviewTaskPo task = workflowReviewService.submitReview(runId, req);
        mergeReviewResult(run, task);
        workflowRunEventService.publishNodeEvent(run.getId(), "REVIEW_SUBMITTED", task.getNodeKey(), task.getReviewAction(),
                Map.of("action", task.getReviewAction(), "comment", task.getReviewComment() == null ? "" : task.getReviewComment()));

        if ("REJECT".equalsIgnoreCase(task.getReviewAction())) {
            markReviewNodeCanceled(run, task);
            run.setStatus("CANCELED");
            run.setError(StringUtils.hasText(task.getReviewComment()) ? task.getReviewComment() : "人工评审拒绝");
            run.setFinishedAt(LocalDateTime.now());
            workflowRunMapper.updateById(run);
            workflowRunEventService.publishRunEvent(run.getId(), "RUN_CANCELED", run.getStatus(),
                    Map.of("error", run.getError()));
            return getRunDetail(run.getId());
        }

        markReviewNodeSuccess(run, task);
        run.setStatus("RUNNING");
        run.setFinishedAt(null);
        workflowRunMapper.updateById(run);
        workflowRunEventService.publishRunEvent(run.getId(), "RUN_RESUMED", run.getStatus(),
                Map.of("currentNodeKey", run.getCurrentNodeKey()));
        resumeWorkflowAfterCommit(run);
        return getRunDetail(run.getId());
    }

    @Override
    public WorkflowRunResp getLatestRun(Long id) {
        findWorkflowOrThrow(id);
        WorkflowRunPo run = workflowRunMapper.selectOne(Wrappers.lambdaQuery(WorkflowRunPo.class)
                .eq(WorkflowRunPo::getWorkflowId, id)
                .orderByDesc(WorkflowRunPo::getCreatedAt)
                .orderByDesc(WorkflowRunPo::getId)
                .last("LIMIT 1"));
        if (run == null) {
            return null;
        }
        return getRunDetail(run.getId());
    }

    private WorkflowPo findWorkflowOrThrow(Long id) {
        WorkflowPo workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在: " + id);
        }
        return workflow;
    }

    private WorkflowVersionPo findVersionOrThrow(Long workflowId, Integer versionNo) {
        if (versionNo == null || versionNo <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "版本号不合法");
        }
        WorkflowVersionPo version = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersionPo.class)
                .eq(WorkflowVersionPo::getWorkflowId, workflowId)
                .eq(WorkflowVersionPo::getVersionNo, versionNo)
                .last("LIMIT 1"));
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流版本不存在: v" + versionNo);
        }
        return version;
    }

    private void saveVersionSnapshot(Long workflowId, String changeSummary) {
        WorkflowDetailResp detail = getDetail(workflowId);
        WorkflowVersionPo latest = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersionPo.class)
                .eq(WorkflowVersionPo::getWorkflowId, workflowId)
                .orderByDesc(WorkflowVersionPo::getVersionNo)
                .orderByDesc(WorkflowVersionPo::getId)
                .last("LIMIT 1"));
        WorkflowVersionPo version = new WorkflowVersionPo();
        version.setWorkflowId(workflowId);
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        version.setChangeSummary(changeSummary == null ? "" : changeSummary);
        version.setSnapshotJson(toJson(detail));
        workflowVersionMapper.insert(version);
    }

    private WorkflowVersionResp toVersionResp(WorkflowVersionPo po) {
        WorkflowVersionResp resp = new WorkflowVersionResp();
        resp.setId(po.getId());
        resp.setWorkflowId(po.getWorkflowId());
        resp.setVersionNo(po.getVersionNo());
        resp.setChangeSummary(po.getChangeSummary());
        resp.setSnapshotJson(parseJsonNode(po.getSnapshotJson()));
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private WorkflowDetailResp parseSnapshot(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, WorkflowDetailResp.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "工作流版本快照解析失败", e);
        }
    }

    private JsonNode parseJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void insertNodesAndEdges(Long workflowId, List<WorkflowNodeDto> nodes, List<WorkflowEdgeDto> edges) {
        List<WorkflowNodePo> nodeRows = nodes.stream()
                .map(node -> toNodePo(workflowId, node))
                .toList();
        nodeMapper.batchInsert(nodeRows);

        List<WorkflowEdgePo> edgeRows = (edges == null ? Collections.<WorkflowEdgeDto>emptyList() : edges)
                .stream()
                .map(edge -> toEdgePo(workflowId, edge))
                .toList();
        if (!edgeRows.isEmpty()) {
            edgeMapper.batchInsert(edgeRows);
        }
    }

    private WorkflowNodePo toNodePo(Long workflowId, WorkflowNodeDto dto) {
        WorkflowNodePo po = new WorkflowNodePo();
        po.setWorkflowId(workflowId);
        po.setNodeKey(dto.getNodeKey());
        po.setNodeType(dto.getNodeType().toUpperCase());
        po.setName(dto.getName());
        po.setConfig(nodeConfigParser.validateAndSerialize(dto.getNodeType(), dto.getConfig()));
        po.setPositionX(dto.getPositionX() == null ? 0 : dto.getPositionX());
        po.setPositionY(dto.getPositionY() == null ? 0 : dto.getPositionY());
        return po;
    }

    private WorkflowEdgePo toEdgePo(Long workflowId, WorkflowEdgeDto dto) {
        WorkflowEdgePo po = new WorkflowEdgePo();
        po.setWorkflowId(workflowId);
        po.setSourceNodeKey(dto.getSourceNodeKey());
        po.setTargetNodeKey(dto.getTargetNodeKey());
        po.setEdgeType(StringUtils.hasText(dto.getEdgeType()) ? dto.getEdgeType() : "DEFAULT");
        po.setConditionExpression(dto.getConditionExpression());
        po.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        return po;
    }

    private WorkflowListItemResp toListItemResp(WorkflowPo po) {
        WorkflowListItemResp resp = new WorkflowListItemResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setEnabled(po.getEnabled());
        resp.setStartNodeKey(po.getStartNodeKey());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private WorkflowDetailResp toDetailResp(WorkflowPo workflow,
                                            List<WorkflowNodePo> nodes,
                                            List<WorkflowEdgePo> edges) {
        WorkflowDetailResp resp = new WorkflowDetailResp();
        resp.setId(workflow.getId());
        resp.setName(workflow.getName());
        resp.setDescription(workflow.getDescription());
        resp.setEnabled(workflow.getEnabled());
        resp.setStartNodeKey(workflow.getStartNodeKey());
        resp.setCreatedAt(workflow.getCreatedAt());
        resp.setUpdatedAt(workflow.getUpdatedAt());
        resp.setNodes(nodes.stream().map(this::toNodeDto).toList());
        resp.setEdges(edges.stream().map(this::toEdgeDto).toList());
        return resp;
    }

    private WorkflowNodeDto toNodeDto(WorkflowNodePo po) {
        WorkflowNodeDto dto = new WorkflowNodeDto();
        dto.setNodeKey(po.getNodeKey());
        dto.setNodeType(po.getNodeType());
        dto.setName(po.getName());
        dto.setConfig(nodeConfigParser.deserialize(po.getConfig()));
        dto.setPositionX(po.getPositionX());
        dto.setPositionY(po.getPositionY());
        return dto;
    }

    private WorkflowEdgeDto toEdgeDto(WorkflowEdgePo po) {
        WorkflowEdgeDto dto = new WorkflowEdgeDto();
        dto.setSourceNodeKey(po.getSourceNodeKey());
        dto.setTargetNodeKey(po.getTargetNodeKey());
        dto.setEdgeType(po.getEdgeType());
        dto.setConditionExpression(po.getConditionExpression());
        dto.setSortOrder(po.getSortOrder());
        return dto;
    }

    private WorkflowRunResp toRunResp(WorkflowRunPo po, List<WorkflowNodeRunPo> nodeRuns) {
        WorkflowRunResp resp = new WorkflowRunResp();
        resp.setId(po.getId());
        resp.setWorkflowId(po.getWorkflowId());
        resp.setWorkflowVersionId(po.getWorkflowVersionId());
        resp.setStatus(po.getStatus());
        resp.setInput(po.getInput());
        resp.setOutput(po.getOutput());
        resp.setError(po.getError());
        resp.setCurrentNodeKey(po.getCurrentNodeKey());
        resp.setTimeoutAt(po.getTimeoutAt());
        resp.setRunMode(po.getRunMode());
        resp.setElapsedMs(po.getElapsedMs());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setFinishedAt(po.getFinishedAt());
        resp.setNodeRuns(nodeRuns.stream().map(this::toNodeRunResp).toList());
        return resp;
    }

    private void markRunFailed(WorkflowRunPo run, Exception e) {
        run.setStatus("FAILED");
        run.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        run.setFinishedAt(LocalDateTime.now());
        workflowRunMapper.updateById(run);
        workflowRunEventService.publishRunEvent(run.getId(), "RUN_FAILED", run.getStatus(),
                Map.of("error", run.getError()));
    }

    private void resumeWorkflowAfterCommit(WorkflowRunPo run) {
        Runnable resumeTask = () -> {
            try {
                llmExecutor.execute(TraceContext.wrap(() -> {
                    try {
                        workflowEngine.resumeAfterReview(run.getId());
                    } catch (Exception e) {
                        log.warn("workflow resume after review failed runId={}: {}", run.getId(), e.getMessage());
                    }
                }));
            } catch (Exception e) {
                markRunFailed(run, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    resumeTask.run();
                }
            });
            return;
        }
        resumeTask.run();
    }

    private void mergeReviewResult(WorkflowRunPo run, WorkflowReviewTaskPo task) {
        Map<String, Object> snapshot = parseContextSnapshot(run.getContextSnapshot());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("action", task.getReviewAction());
        result.put("comment", task.getReviewComment() == null ? "" : task.getReviewComment());
        result.put("editedContent", task.getEditedContent() == null ? "" : task.getEditedContent());
        result.put("reviewedBy", task.getReviewedBy() == null ? "" : task.getReviewedBy());
        result.put("reviewedAt", task.getReviewedAt() == null ? "" : task.getReviewedAt().toString());
        snapshot.put(task.getNodeKey() + "." + task.getOutputVariable(), result);
        run.setContextSnapshot(toJson(snapshot));
    }

    private void markReviewNodeSuccess(WorkflowRunPo run, WorkflowReviewTaskPo task) {
        updateReviewNodeRun(run, task, "SUCCESS");
    }

    private void markReviewNodeCanceled(WorkflowRunPo run, WorkflowReviewTaskPo task) {
        updateReviewNodeRun(run, task, "CANCELED");
    }

    private void updateReviewNodeRun(WorkflowRunPo run, WorkflowReviewTaskPo task, String status) {
        WorkflowNodeRunPo nodeRun = workflowNodeRunMapper.selectOne(Wrappers.lambdaQuery(WorkflowNodeRunPo.class)
                .eq(WorkflowNodeRunPo::getWorkflowRunId, run.getId())
                .eq(WorkflowNodeRunPo::getNodeKey, task.getNodeKey())
                .eq(WorkflowNodeRunPo::getStatus, "WAITING")
                .orderByDesc(WorkflowNodeRunPo::getId)
                .last("LIMIT 1"));
        if (nodeRun == null) {
            return;
        }
        nodeRun.setStatus(status);
        nodeRun.setOutputs(run.getContextSnapshot());
        nodeRun.setFinishedAt(LocalDateTime.now());
        workflowNodeRunMapper.updateById(nodeRun);
    }

    private Map<String, Object> parseContextSnapshot(String snapshot) {
        if (!StringUtils.hasText(snapshot)) {
            return new java.util.LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(snapshot, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("failed to parse workflow context snapshot: {}", e.getMessage());
            return new java.util.LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("failed to serialize workflow context snapshot: {}", e.getMessage());
            return "{}";
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "工作流版本快照序列化失败", e);
        }
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }

    private static String shortError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static boolean isTerminalRun(String status) {
        return List.of("SUCCESS", "FAILED", "TIMEOUT", "CANCELED").contains(status);
    }

    private WorkflowNodeRunResp toNodeRunResp(WorkflowNodeRunPo po) {
        WorkflowNodeRunResp resp = new WorkflowNodeRunResp();
        resp.setId(po.getId());
        resp.setWorkflowRunId(po.getWorkflowRunId());
        resp.setNodeKey(po.getNodeKey());
        resp.setNodeType(po.getNodeType());
        resp.setStatus(po.getStatus());
        resp.setOutputs(parseOutputs(po.getOutputs()));
        resp.setError(po.getError());
        resp.setElapsedMs(po.getElapsedMs());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setFinishedAt(po.getFinishedAt());
        return resp;
    }

    private Map<String, Object> parseOutputs(String outputs) {
        if (!StringUtils.hasText(outputs)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(outputs, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("failed to parse workflow node outputs: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static void validateDefinition(CreateWorkflowReq req) {
        Set<String> nodeKeys = new HashSet<>();
        for (WorkflowNodeDto node : req.getNodes()) {
            if (!nodeKeys.add(node.getNodeKey())) {
                throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                        "节点 key 重复: " + node.getNodeKey());
            }
        }
        if (!nodeKeys.contains(req.getStartNodeKey())) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "开始节点不存在: " + req.getStartNodeKey());
        }
        List<String> missing = new ArrayList<>();
        if (req.getEdges() != null) {
            for (WorkflowEdgeDto edge : req.getEdges()) {
                if (!nodeKeys.contains(edge.getSourceNodeKey())) {
                    missing.add(edge.getSourceNodeKey());
                }
                if (!nodeKeys.contains(edge.getTargetNodeKey())) {
                    missing.add(edge.getTargetNodeKey());
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "连线引用了不存在的节点: " + String.join(",", missing));
        }
    }

    private static int normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 1 ? 1 : 0;
    }
}
