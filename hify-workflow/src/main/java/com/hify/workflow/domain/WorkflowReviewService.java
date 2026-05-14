package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.workflow.api.SubmitWorkflowReviewReq;
import com.hify.workflow.api.WorkflowReviewQuery;
import com.hify.workflow.api.WorkflowReviewTaskResp;
import com.hify.workflow.infra.WorkflowReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowReviewService implements WorkflowReviewHandler {

    private static final String STATUS_WAITING = "WAITING";

    private final WorkflowReviewTaskMapper workflowReviewTaskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void createWaitingReview(Long workflowRunId, String nodeKey, String title, String content,
                                    List<String> actions, boolean allowEdit, String outputVariable) {
        WorkflowReviewTaskPo po = new WorkflowReviewTaskPo();
        po.setWorkflowRunId(workflowRunId);
        po.setNodeKey(nodeKey);
        po.setStatus(STATUS_WAITING);
        po.setTitle(StringUtils.hasText(title) ? title : "人工审核");
        po.setContent(content == null ? "" : content);
        po.setActionsJson(toJson(actions == null || actions.isEmpty() ? List.of("APPROVE", "REJECT") : actions));
        po.setAllowEdit(allowEdit ? 1 : 0);
        po.setOutputVariable(StringUtils.hasText(outputVariable) ? outputVariable : "result");
        workflowReviewTaskMapper.insert(po);
    }

    public WorkflowReviewTaskResp getWaitingReview(Long workflowRunId) {
        WorkflowReviewTaskPo po = workflowReviewTaskMapper.selectOne(Wrappers.lambdaQuery(WorkflowReviewTaskPo.class)
                .eq(WorkflowReviewTaskPo::getWorkflowRunId, workflowRunId)
                .eq(WorkflowReviewTaskPo::getStatus, STATUS_WAITING)
                .orderByDesc(WorkflowReviewTaskPo::getId)
                .last("LIMIT 1"));
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "待评审任务不存在: " + workflowRunId);
        }
        return toResp(po);
    }

    public PageResult<WorkflowReviewTaskResp> listTasks(WorkflowReviewQuery query) {
        Page<WorkflowReviewTaskPo> page = PageHelper.toPage(query.getPage(), query.getSize());
        LocalDateTime now = LocalDateTime.now();
        return PageHelper.toPageResult(workflowReviewTaskMapper.selectPage(page,
                Wrappers.lambdaQuery(WorkflowReviewTaskPo.class)
                        .eq(query.getAssigneeUserId() != null, WorkflowReviewTaskPo::getAssigneeUserId, query.getAssigneeUserId())
                        .eq(StringUtils.hasText(query.getAssigneeUsername()), WorkflowReviewTaskPo::getAssigneeUsername, query.getAssigneeUsername())
                        .eq(StringUtils.hasText(query.getStatus()), WorkflowReviewTaskPo::getStatus, query.getStatus())
                        .le(query.getDueBefore() != null, WorkflowReviewTaskPo::getDueAt, query.getDueBefore())
                        .le(Boolean.TRUE.equals(query.getExpiredOnly()), WorkflowReviewTaskPo::getDueAt, now)
                        .orderByAsc(WorkflowReviewTaskPo::getDueAt)
                        .orderByDesc(WorkflowReviewTaskPo::getCreatedAt)), this::toResp);
    }

    public WorkflowReviewTaskPo submitReview(Long workflowRunId, SubmitWorkflowReviewReq req) {
        WorkflowReviewTaskPo po = workflowReviewTaskMapper.selectOne(Wrappers.lambdaQuery(WorkflowReviewTaskPo.class)
                .eq(WorkflowReviewTaskPo::getWorkflowRunId, workflowRunId)
                .eq(WorkflowReviewTaskPo::getStatus, STATUS_WAITING)
                .orderByDesc(WorkflowReviewTaskPo::getId)
                .last("LIMIT 1"));
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "待评审任务不存在: " + workflowRunId);
        }
        String action = req.getAction().trim().toUpperCase();
        List<String> actions = parseActions(po.getActionsJson());
        if (!actions.stream().map(String::toUpperCase).toList().contains(action)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的评审动作: " + req.getAction());
        }
        po.setStatus(action);
        po.setReviewAction(action);
        po.setReviewComment(req.getComment());
        po.setEditedContent(req.getEditedContent());
        po.setReviewedBy("admin");
        po.setReviewedAt(LocalDateTime.now());
        workflowReviewTaskMapper.updateById(po);
        return po;
    }

    public WorkflowReviewTaskResp toResp(WorkflowReviewTaskPo po) {
        WorkflowReviewTaskResp resp = new WorkflowReviewTaskResp();
        resp.setId(po.getId());
        resp.setWorkflowRunId(po.getWorkflowRunId());
        resp.setNodeKey(po.getNodeKey());
        resp.setStatus(po.getStatus());
        resp.setTitle(po.getTitle());
        resp.setContent(po.getContent());
        resp.setActions(parseActions(po.getActionsJson()));
        resp.setAllowEdit(po.getAllowEdit() != null && po.getAllowEdit() == 1);
        resp.setOutputVariable(po.getOutputVariable());
        resp.setAssigneeUserId(po.getAssigneeUserId());
        resp.setAssigneeUsername(po.getAssigneeUsername());
        resp.setDueAt(po.getDueAt());
        resp.setTimeoutAction(po.getTimeoutAction());
        resp.setNotifiedAt(po.getNotifiedAt());
        resp.setExpiredAt(po.getExpiredAt());
        resp.setReviewAction(po.getReviewAction());
        resp.setReviewComment(po.getReviewComment());
        resp.setReviewedBy(po.getReviewedBy());
        resp.setReviewedAt(po.getReviewedAt());
        return resp;
    }

    private List<String> parseActions(String actionsJson) {
        if (!StringUtils.hasText(actionsJson)) {
            return List.of("APPROVE", "REJECT");
        }
        try {
            return objectMapper.readValue(actionsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("failed to parse workflow review actions: {}", e.getMessage());
            return List.of("APPROVE", "REJECT");
        }
    }

    private String toJson(List<String> actions) {
        try {
            return objectMapper.writeValueAsString(actions == null ? Collections.emptyList() : actions);
        } catch (Exception e) {
            log.warn("failed to serialize workflow review actions: {}", e.getMessage());
            return "[\"APPROVE\",\"REJECT\"]";
        }
    }
}
