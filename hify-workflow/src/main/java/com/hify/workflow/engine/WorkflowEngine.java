package com.hify.workflow.engine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmApiException;
import com.hify.workflow.domain.WorkflowEdgePo;
import com.hify.workflow.domain.WorkflowNodePo;
import com.hify.workflow.domain.WorkflowNodeRunPo;
import com.hify.workflow.domain.WorkflowRunPo;
import com.hify.workflow.domain.config.NodeConfigParser;
import com.hify.workflow.engine.executor.ConditionNodeConfig;
import com.hify.workflow.engine.executor.EndNodeConfig;
import com.hify.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.workflow.infra.WorkflowEdgeMapper;
import com.hify.workflow.infra.WorkflowNodeMapper;
import com.hify.workflow.infra.WorkflowNodeRunMapper;
import com.hify.workflow.infra.WorkflowRunMapper;
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
    private static final String RUN_MODE_SYNC = "SYNC";
    private static final int MAX_STEPS = 50;

    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final NodeConfigParser nodeConfigParser;
    private final NodeExecutorRegistry nodeExecutorRegistry;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowNodeRunMapper workflowNodeRunMapper;
    private final ObjectMapper objectMapper;

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

    private String executeRun(WorkflowRunPo workflowRun, Long workflowId, String userMessage) {
        long startedAt = System.currentTimeMillis();
        List<WorkflowNodePo> nodes = loadNodes(workflowId);
        List<WorkflowEdgePo> edges = loadEdges(workflowId);
        Map<String, WorkflowNodePo> nodeMap = toNodeMap(nodes);
        Map<String, List<WorkflowEdgePo>> edgeMap = toEdgeMap(edges);
        WorkflowNodePo startNode = requireStartNode(nodes);
        ExecutionContext ctx = new ExecutionContext(workflowRun.getId(), userMessage);

        try {
            String currentKey = startNode.getNodeKey();
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

                try {
                    if ("END".equalsIgnoreCase(current.getNodeType())) {
                        output = resolveEndOutput(current, ctx);
                        updateNodeRunSuccess(nodeRun, ctx, nodeStartedAt);
                        break;
                    }
                    if (!"START".equalsIgnoreCase(current.getNodeType())) {
                        NodeConfigDef config = nodeConfigParser.parseExecutionConfig(current.getNodeType(), current.getConfig());
                        nodeExecutorRegistry.get(current.getNodeType()).execute(toEngineNode(current), config, ctx);
                    }
                    checkTimeout(workflowRun);
                    updateNodeRunSuccess(nodeRun, ctx, nodeStartedAt);
                    currentKey = findNext(current, edgeMap, ctx);
                } catch (Exception e) {
                    updateNodeRunFailed(nodeRun, e, nodeStartedAt);
                    throw e;
                }
            }

            updateWorkflowRunSuccess(workflowRun, output, startedAt);
            return output;
        } catch (Exception e) {
            updateWorkflowRunFailed(workflowRun, e, startedAt);
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
        List<WorkflowEdgePo> edges = edgeMap.getOrDefault(current.getNodeKey(), List.of());
        if ("CONDITION".equalsIgnoreCase(current.getNodeType())) {
            Object result = ctx.get(current.getNodeKey(), conditionOutputVariable(current));
            String expected = String.valueOf(Boolean.TRUE.equals(result));
            return edges.stream()
                    .filter(edge -> expected.equalsIgnoreCase(edge.getConditionExpression()))
                    .findFirst()
                    .map(WorkflowEdgePo::getTargetNodeKey)
                    .orElse(null);
        }
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

    public WorkflowRunPo createWorkflowRun(Long workflowId, String userMessage, String runMode, LocalDateTime timeoutAt) {
        WorkflowRunPo po = new WorkflowRunPo();
        po.setWorkflowId(workflowId);
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
