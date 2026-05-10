package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.api.SubmitWorkflowReviewReq;
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
