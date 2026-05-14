package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.api.WorkflowTriggerReq;
import com.hify.workflow.api.WorkflowTriggerResp;
import com.hify.workflow.infra.WorkflowTriggerMapper;
import com.hify.workflow.infra.WorkflowVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowTriggerService {

    private final WorkflowTriggerMapper workflowTriggerMapper;
    private final WorkflowVersionMapper workflowVersionMapper;

    public WorkflowTriggerResp create(Long workflowId, WorkflowTriggerReq req) {
        String triggerType = normalizeType(req.getTriggerType());
        WorkflowTriggerPo po = new WorkflowTriggerPo();
        po.setWorkflowId(workflowId);
        po.setWorkflowVersionId(resolveVersionId(workflowId, req.getVersionNo()));
        po.setTriggerType(triggerType);
        po.setEnabled(req.getEnabled() == null ? 1 : (req.getEnabled() == 1 ? 1 : 0));
        if ("WEBHOOK".equals(triggerType)) {
            po.setTriggerKey("wf-hook-" + workflowId + "-" + System.currentTimeMillis());
        } else {
            if (!StringUtils.hasText(req.getCronExpression())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "定时触发器 cronExpression 不能为空");
            }
            po.setCronExpression(req.getCronExpression());
            po.setNextFireAt(req.getNextFireAt());
        }
        workflowTriggerMapper.insert(po);
        return toResp(po);
    }

    public List<WorkflowTriggerResp> list(Long workflowId) {
        return workflowTriggerMapper.selectList(Wrappers.lambdaQuery(WorkflowTriggerPo.class)
                        .eq(WorkflowTriggerPo::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowTriggerPo::getCreatedAt))
                .stream()
                .map(this::toResp)
                .toList();
    }

    public WorkflowTriggerPo findWebhook(String triggerKey) {
        WorkflowTriggerPo trigger = workflowTriggerMapper.selectOne(Wrappers.lambdaQuery(WorkflowTriggerPo.class)
                .eq(WorkflowTriggerPo::getTriggerKey, triggerKey)
                .eq(WorkflowTriggerPo::getTriggerType, "WEBHOOK")
                .eq(WorkflowTriggerPo::getEnabled, 1)
                .last("LIMIT 1"));
        if (trigger == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Webhook 触发器不存在或已禁用");
        }
        return trigger;
    }

    public void markFired(WorkflowTriggerPo trigger, Long runId) {
        trigger.setLastFireAt(LocalDateTime.now());
        trigger.setLastRunId(runId);
        workflowTriggerMapper.updateById(trigger);
    }

    private Long resolveVersionId(Long workflowId, Integer versionNo) {
        WorkflowVersionPo version = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersionPo.class)
                .eq(WorkflowVersionPo::getWorkflowId, workflowId)
                .eq(versionNo != null && versionNo > 0, WorkflowVersionPo::getVersionNo, versionNo)
                .orderByDesc(WorkflowVersionPo::getVersionNo)
                .orderByDesc(WorkflowVersionPo::getId)
                .last("LIMIT 1"));
        return version == null ? null : version.getId();
    }

    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase();
        if (!List.of("WEBHOOK", "SCHEDULE").contains(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的工作流触发器类型: " + value);
        }
        return type;
    }

    private WorkflowTriggerResp toResp(WorkflowTriggerPo po) {
        WorkflowTriggerResp resp = new WorkflowTriggerResp();
        resp.setId(po.getId());
        resp.setWorkflowId(po.getWorkflowId());
        resp.setWorkflowVersionId(po.getWorkflowVersionId());
        resp.setTriggerType(po.getTriggerType());
        resp.setTriggerKey(po.getTriggerKey());
        resp.setCronExpression(po.getCronExpression());
        resp.setEnabled(po.getEnabled());
        resp.setNextFireAt(po.getNextFireAt());
        resp.setLastFireAt(po.getLastFireAt());
        resp.setLastRunId(po.getLastRunId());
        return resp;
    }
}
