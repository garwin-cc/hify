package com.hify.workflow.engine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmApiException;
import com.hify.workflow.domain.WorkflowEdgePo;
import com.hify.workflow.domain.WorkflowEventPublisher;
import com.hify.workflow.domain.WorkflowNodePo;
import com.hify.workflow.domain.WorkflowNodeRunPo;
import com.hify.workflow.domain.WorkflowRunPo;
import com.hify.workflow.domain.WorkflowVersionPo;
import com.hify.workflow.domain.WorkflowReviewHandler;
import com.hify.workflow.domain.config.NodeConfigParser;
import com.hify.workflow.engine.executor.ConditionNodeConfig;
import com.hify.workflow.engine.executor.EndNodeConfig;
import com.hify.workflow.engine.executor.HumanReviewConfig;
import com.hify.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.workflow.infra.WorkflowEdgeMapper;
import com.hify.workflow.infra.WorkflowNodeMapper;
import com.hify.workflow.infra.WorkflowNodeRunMapper;
import com.hify.workflow.infra.WorkflowRunMapper;
import com.hify.workflow.infra.WorkflowVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_TIMEOUT = "TIMEOUT";
    private static final String STATUS_WAITING = "WAITING";
    private static final String RUN_MODE_SYNC = "SYNC";
    private static final int MAX_STEPS = 50;

    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final NodeConfigParser nodeConfigParser;
    private final NodeExecutorRegistry nodeExecutorRegistry;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowNodeRunMapper workflowNodeRunMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final ObjectMapper objectMapper;
    private final WorkflowEventPublisher workflowEventPublisher;
    private final WorkflowReviewHandler workflowReviewHandler;

    public String execute(Long workflowId, String userMessage) {
        WorkflowRunPo workflowRun = createWorkflowRun(workflowId, userMessage, RUN_MODE_SYNC, null);
        return executeRun(workflowRun, workflowId, userMessage);
    }

    public String executeExistingRun(Long workflowRunId, Long workflowId, String userMessage) {
        WorkflowRunPo workflowRun = workflowRunMapper.selectById(workflowRunId);
        if (workflowRun == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流执行记录不存在: " + workflowRunId);
        }
        return executeRun(workflowRun, workflowId, userMessage);
    }

    public String resumeAfterReview(Long workflowRunId) {
        WorkflowRunPo workflowRun = workflowRunMapper.selectById(workflowRunId);
        if (workflowRun == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流执行记录不存在: " + workflowRunId);
        }
        return executeRun(workflowRun, workflowRun.getWorkflowId(), workflowRun.getInput(), true);
    }

    public Map<String, Object> debugNode(Long workflowId, String nodeKey, String userMessage, Map<String, Object> variables) {
        WorkflowNodePo node = loadNodes(workflowId).stream()
                .filter(item -> item.getNodeKey().equals(nodeKey))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "工作流节点不存在: " + nodeKey));
        if ("START".equalsIgnoreCase(node.getNodeType()) || "END".equalsIgnoreCase(node.getNodeType())) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "START/END 节点不支持单节点调试");
        }
        ExecutionContext ctx = variables == null || variables.isEmpty()
                ? new ExecutionContext(0L, userMessage == null ? "" : userMessage)
                : new ExecutionContext(0L, variables);
        if (ctx.get("start", "userMessage") == null) {
            ctx.set("start", "userMessage", userMessage == null ? "" : userMessage);
        }
        NodeConfigDef config = nodeConfigParser.parseExecutionConfig(node.getNodeType(), node.getConfig());
        nodeExecutorRegistry.get(node.getNodeType()).execute(toEngineNode(node), config, ctx);
        return ctx.snapshot();
    }

    private String executeRun(WorkflowRunPo workflowRun, Long workflowId, String userMessage) {
        return executeRun(workflowRun, workflowId, userMessage, false);
    }

    private String executeRun(WorkflowRunPo workflowRun, Long workflowId, String userMessage, boolean resume) {
        long startedAt = System.currentTimeMillis();
        List<WorkflowNodePo> nodes = loadNodes(workflowId);
        List<WorkflowEdgePo> edges = loadEdges(workflowId);
        Map<String, WorkflowNodePo> nodeMap = toNodeMap(nodes);
        Map<String, List<WorkflowEdgePo>> edgeMap = toEdgeMap(edges);
        WorkflowNodePo startNode = requireStartNode(nodes);
        ExecutionContext ctx = resume
                ? new ExecutionContext(workflowRun.getId(), parseContextSnapshot(workflowRun.getContextSnapshot()))
                : new ExecutionContext(workflowRun.getId(), userMessage);

        try {
            workflowEventPublisher.publishRunEvent(workflowRun.getId(), "RUN_STARTED", STATUS_RUNNING,
                    Map.of("workflowId", workflowId,
                            "runMode", StringUtils.hasText(workflowRun.getRunMode()) ? workflowRun.getRunMode() : RUN_MODE_SYNC));
            String currentKey = resume && StringUtils.hasText(workflowRun.getCurrentNodeKey())
                    ? findNext(nodeMap.get(workflowRun.getCurrentNodeKey()), edgeMap, ctx)
                    : startNode.getNodeKey();
            String output = null;
            int steps = 0;
            while (StringUtils.hasText(currentKey)) {
                if (++steps > MAX_STEPS) {
                    throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED,
                            "工作流执行步数超过 " + MAX_STEPS + "，可能存在循环配置");
                }
                checkTimeout(workflowRun);
                WorkflowNodePo current = nodeMap.get(currentKey);
                if (current == null) {
                    throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "目标节点不存在: " + currentKey);
                }
                updateWorkflowRunCurrentNode(workflowRun, currentKey);
                WorkflowNodeRunPo nodeRun = createNodeRun(workflowRun.getId(), current);
                long nodeStartedAt = System.currentTimeMillis();
                workflowEventPublisher.publishNodeEvent(workflowRun.getId(), "NODE_STARTED", currentKey, STATUS_RUNNING,
                        Map.of("nodeType", current.getNodeType(), "nodeName", current.getName()));

                try {
                    if ("END".equalsIgnoreCase(current.getNodeType())) {
                        output = resolveEndOutput(current, ctx);
                        updateNodeRunSuccess(nodeRun, ctx, nodeStartedAt);
                        workflowEventPublisher.publishNodeEvent(workflowRun.getId(), "NODE_SUCCEEDED", currentKey, STATUS_SUCCESS,
                                Map.of("nodeType", current.getNodeType(), "elapsedMs", elapsed(nodeStartedAt)));
                        break;
                    }
                    if ("HUMAN_REVIEW".equalsIgnoreCase(current.getNodeType())) {
                        handleHumanReview(workflowRun, current, ctx, nodeRun, nodeStartedAt);
                        return null;
                    }
                    if (!"START".equalsIgnoreCase(current.getNodeType())) {
                        NodeConfigDef config = nodeConfigParser.parseExecutionConfig(current.getNodeType(), current.getConfig());
                        nodeExecutorRegistry.get(current.getNodeType()).execute(toEngineNode(current), config, ctx);
                    }
                    checkTimeout(workflowRun);
                    updateNodeRunSuccess(nodeRun, ctx, nodeStartedAt);
                    workflowEventPublisher.publishNodeEvent(workflowRun.getId(), "NODE_SUCCEEDED", currentKey, STATUS_SUCCESS,
                            Map.of("nodeType", current.getNodeType(), "elapsedMs", elapsed(nodeStartedAt)));
                    currentKey = findNext(current, edgeMap, ctx);
                } catch (Exception e) {
                    updateNodeRunFailed(nodeRun, e, nodeStartedAt);
                    workflowEventPublisher.publishNodeEvent(workflowRun.getId(), "NODE_FAILED", currentKey, STATUS_FAILED,
                            Map.of("nodeType", current.getNodeType(), "error", shortError(e), "elapsedMs", elapsed(nodeStartedAt)));
                    throw e;
                }
            }

            updateWorkflowRunSuccess(workflowRun, output, startedAt);
            workflowEventPublisher.publishRunEvent(workflowRun.getId(), "RUN_SUCCEEDED", STATUS_SUCCESS,
                    Map.of("output", output == null ? "" : output, "elapsedMs", elapsed(startedAt)));
            return output;
        } catch (Exception e) {
            updateWorkflowRunFailed(workflowRun, e, startedAt);
            String status = isTimeout(e) ? STATUS_TIMEOUT : STATUS_FAILED;
            workflowEventPublisher.publishRunEvent(workflowRun.getId(),
                    STATUS_TIMEOUT.equals(status) ? "RUN_TIMEOUT" : "RUN_FAILED",
                    status,
                    Map.of("error", shortError(e), "elapsedMs", elapsed(startedAt)));
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED, "工作流执行失败", e);
        }
    }

    private List<WorkflowNodePo> loadNodes(Long workflowId) {
        return workflowNodeMapper.selectList(Wrappers.lambdaQuery(WorkflowNodePo.class)
                .eq(WorkflowNodePo::getWorkflowId, workflowId)
                .orderByAsc(WorkflowNodePo::getId));
    }

    private List<WorkflowEdgePo> loadEdges(Long workflowId) {
        return workflowEdgeMapper.selectList(Wrappers.lambdaQuery(WorkflowEdgePo.class)
                .eq(WorkflowEdgePo::getWorkflowId, workflowId)
                .orderByAsc(WorkflowEdgePo::getSortOrder)
                .orderByAsc(WorkflowEdgePo::getId));
    }

    private Map<String, WorkflowNodePo> toNodeMap(List<WorkflowNodePo> nodes) {
        return nodes.stream().collect(Collectors.toMap(
                WorkflowNodePo::getNodeKey,
                node -> node,
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private Map<String, List<WorkflowEdgePo>> toEdgeMap(List<WorkflowEdgePo> edges) {
        return edges.stream()
                .sorted(Comparator.comparing(edge -> edge.getSortOrder() == null ? 0 : edge.getSortOrder()))
                .collect(Collectors.groupingBy(WorkflowEdgePo::getSourceNodeKey, LinkedHashMap::new, Collectors.toList()));
    }

    private WorkflowNodePo requireStartNode(List<WorkflowNodePo> nodes) {
        return nodes.stream()
                .filter(node -> "START".equalsIgnoreCase(node.getNodeType()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "找不到 START 节点"));
    }

    private String findNext(WorkflowNodePo current, Map<String, List<WorkflowEdgePo>> edgeMap, ExecutionContext ctx) {
        if (current == null) {
            return null;
        }
        List<WorkflowEdgePo> edges = edgeMap.getOrDefault(current.getNodeKey(), List.of());
        if ("HUMAN_REVIEW".equalsIgnoreCase(current.getNodeType())) {
            String action = extractReviewAction(ctx.get(current.getNodeKey(), humanReviewOutputVariable(current)));
            if (StringUtils.hasText(action)) {
                return edges.stream()
                        .filter(edge -> action.equalsIgnoreCase(edge.getConditionExpression()))
                        .findFirst()
                        .map(WorkflowEdgePo::getTargetNodeKey)
                        .orElseGet(() -> defaultNext(edges));
            }
        }
        if ("CONDITION".equalsIgnoreCase(current.getNodeType())) {
            Object result = ctx.get(current.getNodeKey(), conditionOutputVariable(current));
            String expected = String.valueOf(Boolean.TRUE.equals(result));
            return edges.stream()
                    .filter(edge -> expected.equalsIgnoreCase(edge.getConditionExpression()))
                    .findFirst()
                    .map(WorkflowEdgePo::getTargetNodeKey)
                    .orElse(null);
        }
        return defaultNext(edges);
    }

    private String defaultNext(List<WorkflowEdgePo> edges) {
        return edges.stream()
                .filter(edge -> !StringUtils.hasText(edge.getConditionExpression()))
                .findFirst()
                .map(WorkflowEdgePo::getTargetNodeKey)
                .orElseGet(() -> edges.isEmpty() ? null : edges.get(0).getTargetNodeKey());
    }

    private String conditionOutputVariable(WorkflowNodePo current) {
        NodeConfigDef config = nodeConfigParser.parseExecutionConfig(current.getNodeType(), current.getConfig());
        ConditionNodeConfig conditionConfig = (ConditionNodeConfig) config;
        return StringUtils.hasText(conditionConfig.outputVariable()) ? conditionConfig.outputVariable() : "output";
    }

    private String humanReviewOutputVariable(WorkflowNodePo current) {
        NodeConfigDef config = nodeConfigParser.parseExecutionConfig(current.getNodeType(), current.getConfig());
        HumanReviewConfig reviewConfig = (HumanReviewConfig) config;
        return StringUtils.hasText(reviewConfig.outputVariable()) ? reviewConfig.outputVariable() : "result";
    }

    private String extractReviewAction(Object result) {
        if (result instanceof Map<?, ?> map) {
            Object action = map.get("action");
            return action == null ? null : String.valueOf(action);
        }
        return result == null ? null : String.valueOf(result);
    }

    private String resolveEndOutput(WorkflowNodePo endNode, ExecutionContext ctx) {
        NodeConfigDef config = nodeConfigParser.parseExecutionConfig(endNode.getNodeType(), endNode.getConfig());
        EndNodeConfig endConfig = (EndNodeConfig) config;
        if (!StringUtils.hasText(endConfig.outputVariable())) {
            return null;
        }
        Object value = ctx.snapshot().get(endConfig.outputVariable());
        return value == null ? null : String.valueOf(value);
    }

    private WorkflowNode toEngineNode(WorkflowNodePo po) {
        return new WorkflowNode(po.getNodeKey(), po.getNodeType(), po.getName());
    }

    private void handleHumanReview(WorkflowRunPo workflowRun, WorkflowNodePo current, ExecutionContext ctx,
                                   WorkflowNodeRunPo nodeRun, long nodeStartedAt) {
        HumanReviewConfig config = (HumanReviewConfig) nodeConfigParser.parseExecutionConfig(current.getNodeType(), current.getConfig());
        String outputVariable = StringUtils.hasText(config.outputVariable()) ? config.outputVariable() : "result";
        String title = ctx.resolve(StringUtils.hasText(config.title()) ? config.title() : current.getName());
        String content = ctx.resolve(config.content());
        List<String> actions = config.actions() == null || config.actions().isEmpty()
                ? List.of("APPROVE", "REJECT")
                : config.actions();
        boolean allowEdit = Boolean.TRUE.equals(config.allowEdit());
        workflowReviewHandler.createWaitingReview(workflowRun.getId(), current.getNodeKey(), title, content,
                actions, allowEdit, outputVariable);
        updateNodeRunWaiting(nodeRun, ctx, nodeStartedAt);
        updateWorkflowRunWaiting(workflowRun, current.getNodeKey(), ctx);
        workflowEventPublisher.publishNodeEvent(workflowRun.getId(), "REVIEW_WAITING", current.getNodeKey(), STATUS_WAITING,
                Map.of("title", title, "content", content, "actions", actions, "allowEdit", allowEdit));
    }

    public WorkflowRunPo createWorkflowRun(Long workflowId, String userMessage, String runMode, LocalDateTime timeoutAt) {
        WorkflowRunPo po = new WorkflowRunPo();
        po.setWorkflowId(workflowId);
        WorkflowVersionPo version = latestVersion(workflowId);
        if (version != null) {
            po.setWorkflowVersionId(version.getId());
        }
        po.setStatus(STATUS_RUNNING);
        po.setInput(userMessage);
        po.setRunMode(StringUtils.hasText(runMode) ? runMode : RUN_MODE_SYNC);
        po.setTimeoutAt(timeoutAt);
        try {
            workflowRunMapper.insert(po);
        } catch (Exception e) {
            log.warn("failed to create workflow run record workflowId={}: {}", workflowId, e.getMessage());
        }
        return po;
    }

    private WorkflowVersionPo latestVersion(Long workflowId) {
        try {
            return workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersionPo.class)
                    .eq(WorkflowVersionPo::getWorkflowId, workflowId)
                    .orderByDesc(WorkflowVersionPo::getVersionNo)
                    .orderByDesc(WorkflowVersionPo::getId)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("failed to query workflow latest version workflowId={}: {}", workflowId, e.getMessage());
            return null;
        }
    }

    private void updateWorkflowRunCurrentNode(WorkflowRunPo po, String currentNodeKey) {
        po.setCurrentNodeKey(currentNodeKey);
        try {
            workflowRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("failed to update workflow run current node id={}: {}", po.getId(), e.getMessage());
        }
    }

    private WorkflowNodeRunPo createNodeRun(Long workflowRunId, WorkflowNodePo node) {
        WorkflowNodeRunPo po = new WorkflowNodeRunPo();
        po.setWorkflowRunId(workflowRunId);
        po.setNodeKey(node.getNodeKey());
        po.setNodeType(node.getNodeType());
        po.setStatus(STATUS_RUNNING);
        try {
            workflowNodeRunMapper.insert(po);
        } catch (Exception e) {
            log.warn("failed to create workflow node run record runId={} nodeKey={}: {}",
                    workflowRunId, node.getNodeKey(), e.getMessage());
        }
        return po;
    }

    private void updateNodeRunSuccess(WorkflowNodeRunPo po, ExecutionContext ctx, long startedAt) {
        po.setStatus(STATUS_SUCCESS);
        po.setOutputs(toJson(ctx.snapshot()));
        po.setElapsedMs(elapsed(startedAt));
        po.setFinishedAt(LocalDateTime.now());
        try {
            workflowNodeRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("failed to update workflow node run success id={}: {}", po.getId(), e.getMessage());
        }
    }

    private void updateNodeRunWaiting(WorkflowNodeRunPo po, ExecutionContext ctx, long startedAt) {
        po.setStatus(STATUS_WAITING);
        po.setOutputs(toJson(ctx.snapshot()));
        po.setElapsedMs(elapsed(startedAt));
        try {
            workflowNodeRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("failed to update workflow node run waiting id={}: {}", po.getId(), e.getMessage());
        }
    }

    private void updateNodeRunFailed(WorkflowNodeRunPo po, Exception exception, long startedAt) {
        po.setStatus(STATUS_FAILED);
        po.setError(shortError(exception));
        po.setElapsedMs(elapsed(startedAt));
        po.setFinishedAt(LocalDateTime.now());
        try {
            workflowNodeRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("failed to update workflow node run failed id={}: {}", po.getId(), e.getMessage());
        }
    }

    private void updateWorkflowRunSuccess(WorkflowRunPo po, String output, long startedAt) {
        po.setStatus(STATUS_SUCCESS);
        po.setOutput(output);
        po.setElapsedMs(elapsed(startedAt));
        po.setFinishedAt(LocalDateTime.now());
        try {
            workflowRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("failed to update workflow run success id={}: {}", po.getId(), e.getMessage());
        }
    }

    private void updateWorkflowRunWaiting(WorkflowRunPo po, String currentNodeKey, ExecutionContext ctx) {
        po.setStatus(STATUS_WAITING);
        po.setCurrentNodeKey(currentNodeKey);
        po.setContextSnapshot(toJson(ctx.snapshot()));
        try {
            workflowRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("failed to update workflow run waiting id={}: {}", po.getId(), e.getMessage());
        }
    }

    private void updateWorkflowRunFailed(WorkflowRunPo po, Exception exception, long startedAt) {
        po.setStatus(isTimeout(exception) ? STATUS_TIMEOUT : STATUS_FAILED);
        po.setError(shortError(exception));
        po.setElapsedMs(elapsed(startedAt));
        po.setFinishedAt(LocalDateTime.now());
        try {
            workflowRunMapper.updateById(po);
        } catch (Exception e) {
            log.warn("failed to update workflow run failed id={}: {}", po.getId(), e.getMessage());
        }
    }

    private void checkTimeout(WorkflowRunPo po) {
        if (po.getTimeoutAt() != null && LocalDateTime.now().isAfter(po.getTimeoutAt())) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED, "工作流执行超时");
        }
    }

    private boolean isTimeout(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof LlmApiException llmApiException
                    && llmApiException.getType() == LlmApiException.Type.TIMEOUT) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("工作流执行超时") || message.contains("LLM 请求超时"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("failed to serialize workflow context snapshot: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> parseContextSnapshot(String snapshot) {
        if (!StringUtils.hasText(snapshot)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(snapshot, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("failed to parse workflow context snapshot: {}", e.getMessage());
            return Map.of();
        }
    }

    private int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }

    private String shortError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
