package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.audit.AuditLogRecord;
import com.hify.common.audit.AuditLogService;
import com.hify.auth.api.AuthService;
import com.hify.auth.api.CurrentUser;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.log.TraceContext;
import com.hify.common.task.TaskQueue;
import com.hify.common.task.TaskRejectedException;
import com.hify.common.task.TaskRequest;
import com.hify.common.task.TaskType;
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
import com.hify.workflow.api.WorkflowNodeCallTraceResp;
import com.hify.workflow.api.WorkflowNodeRunResp;
import com.hify.workflow.api.WorkflowNodeDto;
import com.hify.workflow.api.WorkflowPublishReq;
import com.hify.workflow.api.WorkflowPublishResp;
import com.hify.workflow.api.WorkflowQuery;
import com.hify.workflow.api.WorkflowReviewQuery;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunQuery;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowReviewTaskResp;
import com.hify.workflow.api.WorkflowRollbackReq;
import com.hify.workflow.api.WorkflowTriggerReq;
import com.hify.workflow.api.WorkflowTriggerResp;
import com.hify.workflow.api.WorkflowVariableResp;
import com.hify.workflow.api.WorkflowVersionDiffResp;
import com.hify.workflow.api.WorkflowVersionResp;
import com.hify.workflow.domain.config.NodeConfigParser;
import com.hify.workflow.engine.WorkflowEngine;
import com.hify.workflow.infra.WorkflowEdgeMapper;
import com.hify.workflow.infra.WorkflowMapper;
import com.hify.workflow.infra.WorkflowNodeMapper;
import com.hify.workflow.infra.WorkflowNodeCallTraceMapper;
import com.hify.workflow.infra.WorkflowNodeRunMapper;
import com.hify.workflow.infra.WorkflowRunMapper;
import com.hify.workflow.infra.WorkflowVersionMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final WorkflowNodeCallTraceMapper workflowNodeCallTraceMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final NodeConfigParser nodeConfigParser;
    private final WorkflowEngine workflowEngine;
    private final WorkflowRunEventService workflowRunEventService;
    private final WorkflowReviewService workflowReviewService;
    private final ObjectMapper objectMapper;
    private AuditLogService auditLogService;
    private AuthService authService;
    private TaskQueue workflowTaskQueue;
    private WorkflowPublishService workflowPublishService;
    private WorkflowVersionDiffService workflowVersionDiffService;
    private WorkflowVariableService workflowVariableService;
    private WorkflowTriggerService workflowTriggerService;
    @Resource(name = "llmExecutor")
    private ThreadPoolExecutor llmExecutor;

    @Autowired(required = false)
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Autowired(required = false)
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    @Autowired(required = false)
    public void setWorkflowTaskQueue(@Qualifier("workflowTaskQueue") TaskQueue workflowTaskQueue) {
        this.workflowTaskQueue = workflowTaskQueue;
    }

    @Autowired(required = false)
    public void setWorkflowPublishService(WorkflowPublishService workflowPublishService) {
        this.workflowPublishService = workflowPublishService;
    }

    @Autowired(required = false)
    public void setWorkflowVersionDiffService(WorkflowVersionDiffService workflowVersionDiffService) {
        this.workflowVersionDiffService = workflowVersionDiffService;
    }

    @Autowired(required = false)
    public void setWorkflowVariableService(WorkflowVariableService workflowVariableService) {
        this.workflowVariableService = workflowVariableService;
    }

    @Autowired(required = false)
    public void setWorkflowTriggerService(WorkflowTriggerService workflowTriggerService) {
        this.workflowTriggerService = workflowTriggerService;
    }

    @Override
    @Transactional
    public WorkflowDetailResp create(CreateWorkflowReq req) {
        validateDefinition(req);
        validateCodeTaskSafety(req.getNodes(), req.getEdges());
        WorkflowPo workflow = new WorkflowPo();
        workflow.setName(req.getName());
        workflow.setDescription(req.getDescription() == null ? "" : req.getDescription());
        workflow.setEnabled(req.getEnabled() == null ? 1 : normalizeEnabled(req.getEnabled()));
        workflow.setStartNodeKey(req.getStartNodeKey());
        workflowMapper.insert(workflow);

        insertNodesAndEdges(workflow.getId(), req.getNodes(), req.getEdges());
        saveVersionSnapshot(workflow.getId(), "创建工作流");
        log.info("created workflow id={} name={}", workflow.getId(), workflow.getName());
        recordWorkflowAudit("WORKFLOW_CREATE", workflow, null, workflowAudit(workflow), true, null);
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
        validateCodeTaskSafety(req.getNodes(), req.getEdges());
        WorkflowPo workflow = findWorkflowOrThrow(id);
        Map<String, Object> before = workflowAudit(workflow);
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
        recordWorkflowAudit("WORKFLOW_UPDATE", workflow, before, workflowAudit(workflow), true, null);
        return getDetail(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        WorkflowPo workflow = findWorkflowOrThrow(id);
        Map<String, Object> before = workflowAudit(workflow);
        workflowMapper.deleteById(id);
        nodeMapper.delete(Wrappers.lambdaQuery(WorkflowNodePo.class)
                .eq(WorkflowNodePo::getWorkflowId, id));
        edgeMapper.delete(Wrappers.lambdaQuery(WorkflowEdgePo.class)
                .eq(WorkflowEdgePo::getWorkflowId, id));
        log.info("deleted workflow id={}", id);
        recordWorkflowAudit("WORKFLOW_DELETE", workflow, before, Map.of(), true, null);
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
        return startAsyncRun(id, req.getUserMessage(), null);
    }

    private WorkflowRunResp startAsyncRun(Long id, String userMessage, Long rerunFromRunId) {
        return startAsyncRun(id, userMessage, rerunFromRunId, "MANUAL", null, null, "MANUAL");
    }

    private WorkflowRunResp startAsyncRun(Long id, String userMessage, Long rerunFromRunId,
                                          String triggerType, Long triggerId, Long publishId, String source) {
        WorkflowRunPo run = workflowEngine.createWorkflowRun(
                id,
                userMessage,
                "ASYNC",
                LocalDateTime.now().plusSeconds(300));
        if (rerunFromRunId != null) {
            run.setRerunFromRunId(rerunFromRunId);
        }
        run.setTriggerType(triggerType);
        run.setTriggerId(triggerId);
        run.setPublishId(publishId);
        run.setSource(StringUtils.hasText(source) ? source : "MANUAL");
        workflowRunMapper.updateById(run);
        try {
            Runnable task = () -> executeAsyncWorkflowRun(run.getId(), id, userMessage);
            if (workflowTaskQueue != null) {
                workflowTaskQueue.submit(TaskRequest.builder()
                        .taskType(TaskType.WORKFLOW_RUN)
                        .taskId("workflow-run-" + run.getId())
                        .task(task)
                        .build());
            } else {
                llmExecutor.execute(TraceContext.wrap(task));
            }
        } catch (TaskRejectedException e) {
            markRunFailed(run, e);
        } catch (Exception e) {
            markRunFailed(run, e);
        }
        return getRunDetail(run.getId());
    }

    private void executeAsyncWorkflowRun(Long runId, Long workflowId, String userMessage) {
        TraceContext.put("workflowId", workflowId);
        TraceContext.put("workflowRunId", runId);
        try {
            workflowEngine.executeExistingRun(runId, workflowId, userMessage);
        } catch (Exception e) {
            log.warn("async workflow run failed runId={} workflowId={}: {}",
                    runId, workflowId, e.getMessage());
        }
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
    public WorkflowVersionDiffResp diffVersions(Long workflowId, Integer leftVersionNo, Integer rightVersionNo) {
        findWorkflowOrThrow(workflowId);
        requireService(workflowVersionDiffService, "工作流版本差异服务不可用");
        return workflowVersionDiffService.diff(workflowId, leftVersionNo, rightVersionNo);
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
        WorkflowDetailResp restored = update(workflowId, req);
        recordWorkflowAudit("WORKFLOW_RESTORE_VERSION", findWorkflowOrThrow(workflowId), null,
                Map.of("versionNo", versionNo), true, null);
        return restored;
    }

    @Override
    @Transactional
    public WorkflowDetailResp rollbackVersion(Long workflowId, Integer versionNo, WorkflowRollbackReq req) {
        WorkflowPo workflow = findWorkflowOrThrow(workflowId);
        WorkflowVersionPo version = findVersionOrThrow(workflowId, versionNo);
        WorkflowDetailResp restored = restoreVersion(workflowId, versionNo);
        WorkflowVersionPo latest = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersionPo.class)
                .eq(WorkflowVersionPo::getWorkflowId, workflowId)
                .orderByDesc(WorkflowVersionPo::getVersionNo)
                .orderByDesc(WorkflowVersionPo::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            latest.setParentVersionId(version.getId());
            latest.setChangeSummary(StringUtils.hasText(req == null ? null : req.getChangeSummary())
                    ? req.getChangeSummary()
                    : "回滚到 v" + versionNo);
            workflowVersionMapper.updateById(latest);
        }
        if (req != null && Boolean.TRUE.equals(req.getPublish())) {
            WorkflowPublishReq publishReq = new WorkflowPublishReq();
            publishReq.setVersionNo(latest == null ? null : latest.getVersionNo());
            publishReq.setPublishType(StringUtils.hasText(req.getPublishType()) ? req.getPublishType() : "API_ENDPOINT");
            publishReq.setDisplayName(workflow.getName());
            publish(workflowId, publishReq);
        }
        recordWorkflowAudit("WORKFLOW_ROLLBACK_VERSION", workflow, null,
                Map.of("versionNo", versionNo), true, null);
        return restored;
    }

    @Override
    public WorkflowPublishResp publish(Long workflowId, WorkflowPublishReq req) {
        findWorkflowOrThrow(workflowId);
        validateCodeTaskSafety(getDetail(workflowId));
        requireService(workflowPublishService, "工作流发布服务不可用");
        WorkflowPublishResp resp = workflowPublishService.publish(workflowId, req);
        recordWorkflowAudit("WORKFLOW_PUBLISH", findWorkflowOrThrow(workflowId), null,
                Map.of("publishType", resp.getPublishType(), "versionId", resp.getWorkflowVersionId()), true, null);
        return resp;
    }

    @Override
    public List<WorkflowPublishResp> listPublishes(Long workflowId) {
        findWorkflowOrThrow(workflowId);
        requireService(workflowPublishService, "工作流发布服务不可用");
        return workflowPublishService.list(workflowId);
    }

    @Override
    public WorkflowTriggerResp createTrigger(Long workflowId, WorkflowTriggerReq req) {
        findWorkflowOrThrow(workflowId);
        requireService(workflowTriggerService, "工作流触发器服务不可用");
        return workflowTriggerService.create(workflowId, req);
    }

    @Override
    public List<WorkflowTriggerResp> listTriggers(Long workflowId) {
        findWorkflowOrThrow(workflowId);
        requireService(workflowTriggerService, "工作流触发器服务不可用");
        return workflowTriggerService.list(workflowId);
    }

    @Override
    public WorkflowRunResp triggerWebhook(String triggerKey, WorkflowRunReq req) {
        requireService(workflowTriggerService, "工作流触发器服务不可用");
        WorkflowTriggerPo trigger = workflowTriggerService.findWebhook(triggerKey);
        WorkflowRunResp run = startAsyncRun(trigger.getWorkflowId(), req.getUserMessage(), null,
                "WEBHOOK", trigger.getId(), null, "WEBHOOK");
        workflowTriggerService.markFired(trigger, run.getId());
        return run;
    }

    @Override
    public List<WorkflowVariableResp> listVariables(Long workflowId) {
        findWorkflowOrThrow(workflowId);
        requireService(workflowVariableService, "工作流变量服务不可用");
        return workflowVariableService.listVariables(workflowId);
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
    public PageResult<WorkflowRunResp> listRuns(WorkflowRunQuery query) {
        int pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        int pageSize = query.getSize() <= 0 ? 20 : query.getSize();
        Page<WorkflowRunPo> page = PageHelper.toPage(pageNo, pageSize);
        return PageHelper.toPageResult(workflowRunMapper.selectPage(page,
                Wrappers.lambdaQuery(WorkflowRunPo.class)
                        .eq(query.getWorkflowId() != null, WorkflowRunPo::getWorkflowId, query.getWorkflowId())
                        .eq(StringUtils.hasText(query.getStatus()), WorkflowRunPo::getStatus, query.getStatus())
                        .eq(StringUtils.hasText(query.getTraceId()), WorkflowRunPo::getTraceId, query.getTraceId())
                        .eq(StringUtils.hasText(query.getRunMode()), WorkflowRunPo::getRunMode, query.getRunMode())
                        .eq(StringUtils.hasText(query.getSource()), WorkflowRunPo::getSource, query.getSource())
                        .ge(query.getCreatedAtStart() != null, WorkflowRunPo::getCreatedAt, query.getCreatedAtStart())
                        .le(query.getCreatedAtEnd() != null, WorkflowRunPo::getCreatedAt, query.getCreatedAtEnd())
                        .orderByDesc(WorkflowRunPo::getCreatedAt)
                        .orderByDesc(WorkflowRunPo::getId)), run -> toRunResp(run, List.of()));
    }

    @Override
    public WorkflowRunResp rerunRun(Long runId) {
        WorkflowRunPo sourceRun = workflowRunMapper.selectById(runId);
        if (sourceRun == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流执行记录不存在: " + runId);
        }
        if (!List.of("FAILED", "TIMEOUT", "CANCELED").contains(sourceRun.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅失败、超时或取消的工作流运行支持重跑");
        }
        findWorkflowOrThrow(sourceRun.getWorkflowId());
        return startAsyncRun(sourceRun.getWorkflowId(), sourceRun.getInput(), sourceRun.getId());
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
    public PageResult<WorkflowReviewTaskResp> listReviewTasks(WorkflowReviewQuery query) {
        return workflowReviewService.listTasks(query);
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
        recordReviewAudit(run, task);

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
        version.setVersionStatus("DRAFT");
        version.setGrayPercent(0);
        version.setChecksum(workflowVersionDiffService == null ? "" : workflowVersionDiffService.checksum(version.getSnapshotJson()));
        workflowVersionMapper.insert(version);
    }

    private WorkflowVersionResp toVersionResp(WorkflowVersionPo po) {
        WorkflowVersionResp resp = new WorkflowVersionResp();
        resp.setId(po.getId());
        resp.setWorkflowId(po.getWorkflowId());
        resp.setVersionNo(po.getVersionNo());
        resp.setChangeSummary(po.getChangeSummary());
        resp.setVersionStatus(po.getVersionStatus());
        resp.setParentVersionId(po.getParentVersionId());
        resp.setGrayPercent(po.getGrayPercent());
        resp.setChecksum(po.getChecksum());
        resp.setPublishedBy(po.getPublishedBy());
        resp.setPublishedAt(po.getPublishedAt());
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
        resp.setTraceId(po.getTraceId());
        resp.setRerunFromRunId(po.getRerunFromRunId());
        resp.setStatus(po.getStatus());
        resp.setInput(po.getInput());
        resp.setOutput(po.getOutput());
        resp.setError(po.getError());
        resp.setCurrentNodeKey(po.getCurrentNodeKey());
        resp.setTimeoutAt(po.getTimeoutAt());
        resp.setRunMode(po.getRunMode());
        resp.setTriggerType(po.getTriggerType());
        resp.setTriggerId(po.getTriggerId());
        resp.setPublishId(po.getPublishId());
        resp.setSource(po.getSource());
        resp.setElapsedMs(po.getElapsedMs());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setFinishedAt(po.getFinishedAt());
        Map<Long, List<WorkflowNodeCallTraceResp>> traceMap = loadCallTraces(po.getId());
        resp.setNodeRuns(nodeRuns.stream().map(nodeRun -> toNodeRunResp(nodeRun, traceMap)).toList());
        return resp;
    }

    private Map<Long, List<WorkflowNodeCallTraceResp>> loadCallTraces(Long workflowRunId) {
        if (workflowNodeCallTraceMapper == null) {
            return Collections.emptyMap();
        }
        List<WorkflowNodeCallTracePo> traces = workflowNodeCallTraceMapper.selectList(
                Wrappers.lambdaQuery(WorkflowNodeCallTracePo.class)
                        .eq(WorkflowNodeCallTracePo::getWorkflowRunId, workflowRunId)
                        .orderByAsc(WorkflowNodeCallTracePo::getId));
        return traces.stream()
                .map(this::toCallTraceResp)
                .filter(trace -> trace.getWorkflowNodeRunId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        WorkflowNodeCallTraceResp::getWorkflowNodeRunId,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
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

    private Map<String, Object> workflowAudit(WorkflowPo workflow) {
        if (workflow == null) {
            return Map.of();
        }
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("id", workflow.getId());
        value.put("workspaceId", workflow.getWorkspaceId());
        value.put("projectId", workflow.getProjectId());
        value.put("name", workflow.getName());
        value.put("description", workflow.getDescription());
        value.put("enabled", workflow.getEnabled());
        value.put("startNodeKey", workflow.getStartNodeKey());
        return value;
    }

    private void recordWorkflowAudit(String action, WorkflowPo workflow, Map<String, Object> before,
                                     Map<String, Object> after, boolean success, String errorMessage) {
        if (auditLogService == null) {
            return;
        }
        CurrentUser user = currentUser();
        auditLogService.record(AuditLogRecord.builder()
                .traceId(TraceContext.ensureTraceId())
                .actorUserId(user == null ? null : user.getId())
                .actorUsername(user == null ? "" : user.getUsername())
                .workspaceId(workflow == null ? null : workflow.getWorkspaceId())
                .projectId(workflow == null ? null : workflow.getProjectId())
                .action(action)
                .resourceType("WORKFLOW")
                .resourceId(workflow == null ? null : workflow.getId())
                .resourceName(workflow == null ? "" : workflow.getName())
                .success(success)
                .errorMessage(errorMessage)
                .before(before)
                .after(after)
                .build());
    }

    private void recordReviewAudit(WorkflowRunPo run, WorkflowReviewTaskPo task) {
        WorkflowPo workflow = workflowMapper.selectById(run.getWorkflowId());
        Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("runId", run.getId());
        after.put("nodeKey", task.getNodeKey());
        after.put("action", task.getReviewAction());
        after.put("comment", task.getReviewComment() == null ? "" : task.getReviewComment());
        after.put("reviewedBy", task.getReviewedBy() == null ? "" : task.getReviewedBy());
        recordWorkflowAudit("WORKFLOW_REVIEW_SUBMIT", workflow, null, after, true, null);
    }

    private CurrentUser currentUser() {
        if (authService == null) {
            return null;
        }
        try {
            return authService.getCurrentUser();
        } catch (Exception ignored) {
            return null;
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

    private WorkflowNodeRunResp toNodeRunResp(WorkflowNodeRunPo po, Map<Long, List<WorkflowNodeCallTraceResp>> traceMap) {
        WorkflowNodeRunResp resp = new WorkflowNodeRunResp();
        resp.setId(po.getId());
        resp.setWorkflowRunId(po.getWorkflowRunId());
        resp.setNodeKey(po.getNodeKey());
        resp.setNodeType(po.getNodeType());
        resp.setStatus(po.getStatus());
        resp.setAttemptNo(po.getAttemptNo());
        resp.setMaxAttempts(po.getMaxAttempts());
        resp.setTimeoutSeconds(po.getTimeoutSeconds());
        resp.setFailureStrategy(po.getFailureStrategy());
        resp.setInputSnapshot(parseOutputs(po.getInputSnapshot()));
        resp.setOutputs(parseOutputs(po.getOutputs()));
        resp.setError(po.getError());
        resp.setElapsedMs(po.getElapsedMs());
        resp.setStartedAt(po.getStartedAt());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setFinishedAt(po.getFinishedAt());
        resp.setCallTraces(traceMap.getOrDefault(po.getId(), Collections.emptyList()));
        return resp;
    }

    private WorkflowNodeCallTraceResp toCallTraceResp(WorkflowNodeCallTracePo po) {
        WorkflowNodeCallTraceResp resp = new WorkflowNodeCallTraceResp();
        resp.setId(po.getId());
        resp.setWorkflowRunId(po.getWorkflowRunId());
        resp.setWorkflowNodeRunId(po.getWorkflowNodeRunId());
        resp.setNodeKey(po.getNodeKey());
        resp.setNodeType(po.getNodeType());
        resp.setCallType(po.getCallType());
        resp.setTarget(po.getTarget());
        resp.setRequestSnapshot(parseOutputs(po.getRequestSnapshot()));
        resp.setResponseSnapshot(parseOutputs(po.getResponseSnapshot()));
        resp.setStatus(po.getStatus());
        resp.setErrorMessage(po.getErrorMessage());
        resp.setDurationMs(po.getDurationMs());
        resp.setStartedAt(po.getStartedAt());
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

    private static void validateCodeTaskSafety(WorkflowDetailResp detail) {
        validateCodeTaskSafety(detail.getNodes(), detail.getEdges());
    }

    private static void validateCodeTaskSafety(List<WorkflowNodeDto> nodes, List<WorkflowEdgeDto> edges) {
        Map<String, WorkflowNodeDto> nodeMap = nodes.stream()
                .collect(java.util.stream.Collectors.toMap(WorkflowNodeDto::getNodeKey, node -> node));
        Map<String, List<WorkflowEdgeDto>> edgeMap = (edges == null ? List.<WorkflowEdgeDto>of() : edges).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        WorkflowEdgeDto::getSourceNodeKey,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        for (WorkflowNodeDto node : nodes) {
            if (!"CODE_TASK".equalsIgnoreCase(node.getNodeType())) {
                continue;
            }
            JsonNode config = node.getConfig();
            String executor = config == null ? "" : config.path("executor").asText("");
            if (StringUtils.hasText(executor) && !"MCP".equalsIgnoreCase(executor)) {
                throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                        "CODE_TASK 只允许通过 MCP Code Worker 执行: " + node.getNodeKey());
            }
            boolean approvalRequired = config != null && config.path("approvalRequired").asBoolean(false);
            if (approvalRequired && !hasDownstreamHumanReview(node.getNodeKey(), nodeMap, edgeMap, new HashSet<>())) {
                throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                        "需要审批的 CODE_TASK 后必须连接 HUMAN_REVIEW: " + node.getNodeKey());
            }
        }
    }

    private static boolean hasDownstreamHumanReview(String nodeKey, Map<String, WorkflowNodeDto> nodeMap,
                                                    Map<String, List<WorkflowEdgeDto>> edgeMap, Set<String> visited) {
        if (!visited.add(nodeKey)) {
            return false;
        }
        for (WorkflowEdgeDto edge : edgeMap.getOrDefault(nodeKey, List.of())) {
            WorkflowNodeDto target = nodeMap.get(edge.getTargetNodeKey());
            if (target == null) {
                continue;
            }
            if ("HUMAN_REVIEW".equalsIgnoreCase(target.getNodeType())) {
                return true;
            }
            if (hasDownstreamHumanReview(target.getNodeKey(), nodeMap, edgeMap, visited)) {
                return true;
            }
        }
        return false;
    }

    private static <T> void requireService(T service, String message) {
        if (service == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, message);
        }
    }

    private static int normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 1 ? 1 : 0;
    }
}
