package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.workflow.api.CreateWorkflowReq;
import com.hify.workflow.api.UpdateWorkflowReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowEdgeDto;
import com.hify.workflow.api.WorkflowListItemResp;
import com.hify.workflow.api.WorkflowNodeRunResp;
import com.hify.workflow.api.WorkflowNodeDto;
import com.hify.workflow.api.WorkflowQuery;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.domain.config.NodeConfigParser;
import com.hify.workflow.engine.WorkflowEngine;
import com.hify.workflow.infra.WorkflowEdgeMapper;
import com.hify.workflow.infra.WorkflowMapper;
import com.hify.workflow.infra.WorkflowNodeMapper;
import com.hify.workflow.infra.WorkflowNodeRunMapper;
import com.hify.workflow.infra.WorkflowRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowNodeRunMapper workflowNodeRunMapper;
    private final NodeConfigParser nodeConfigParser;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

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
        List<WorkflowNodeRunPo> nodeRuns = workflowNodeRunMapper.selectList(
                Wrappers.lambdaQuery(WorkflowNodeRunPo.class)
                        .eq(WorkflowNodeRunPo::getWorkflowRunId, run.getId())
                        .orderByAsc(WorkflowNodeRunPo::getId));
        return toRunResp(run, nodeRuns);
    }

    private WorkflowPo findWorkflowOrThrow(Long id) {
        WorkflowPo workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在: " + id);
        }
        return workflow;
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
        resp.setStatus(po.getStatus());
        resp.setInput(po.getInput());
        resp.setOutput(po.getOutput());
        resp.setError(po.getError());
        resp.setElapsedMs(po.getElapsedMs());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setFinishedAt(po.getFinishedAt());
        resp.setNodeRuns(nodeRuns.stream().map(this::toNodeRunResp).toList());
        return resp;
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
