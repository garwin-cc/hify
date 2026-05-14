package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.api.WorkflowPublishReq;
import com.hify.workflow.api.WorkflowPublishResp;
import com.hify.workflow.infra.WorkflowPublishMapper;
import com.hify.workflow.infra.WorkflowVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowPublishService {

    private static final List<String> PUBLISH_TYPES = List.of("WEB_APP", "API_ENDPOINT", "TOOL");

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowPublishMapper workflowPublishMapper;

    public WorkflowPublishResp publish(Long workflowId, WorkflowPublishReq req) {
        String publishType = normalizePublishType(req.getPublishType());
        WorkflowVersionPo version = findVersion(workflowId, req.getVersionNo());
        WorkflowPublishPo po = new WorkflowPublishPo();
        po.setWorkflowId(workflowId);
        po.setWorkflowVersionId(version.getId());
        po.setPublishType(publishType);
        po.setPublishStatus("ACTIVE");
        po.setDisplayName(StringUtils.hasText(req.getDisplayName()) ? req.getDisplayName() : publishType);
        po.setGrayPercent(clampPercent(req.getGrayPercent()));
        po.setPublishedAt(LocalDateTime.now());
        if ("TOOL".equals(publishType)) {
            po.setToolKey("wf-tool-" + workflowId + "-" + version.getVersionNo());
        } else {
            po.setEndpointKey("wf-" + publishType.toLowerCase().replace('_', '-') + "-" + workflowId + "-" + version.getVersionNo());
        }
        workflowPublishMapper.insert(po);

        version.setVersionStatus(po.getGrayPercent() != null && po.getGrayPercent() > 0 ? "GRAY" : "PUBLISHED");
        version.setGrayPercent(po.getGrayPercent());
        version.setPublishedAt(po.getPublishedAt());
        workflowVersionMapper.updateById(version);
        return toResp(po);
    }

    public List<WorkflowPublishResp> list(Long workflowId) {
        return workflowPublishMapper.selectList(Wrappers.lambdaQuery(WorkflowPublishPo.class)
                        .eq(WorkflowPublishPo::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowPublishPo::getCreatedAt))
                .stream()
                .map(this::toResp)
                .toList();
    }

    public WorkflowPublishPo findActiveByEndpointKey(String endpointKey) {
        return workflowPublishMapper.selectOne(Wrappers.lambdaQuery(WorkflowPublishPo.class)
                .eq(WorkflowPublishPo::getEndpointKey, endpointKey)
                .eq(WorkflowPublishPo::getPublishStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    private WorkflowVersionPo findVersion(Long workflowId, Integer versionNo) {
        WorkflowVersionPo version = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersionPo.class)
                .eq(WorkflowVersionPo::getWorkflowId, workflowId)
                .eq(versionNo != null && versionNo > 0, WorkflowVersionPo::getVersionNo, versionNo)
                .orderByDesc(WorkflowVersionPo::getVersionNo)
                .orderByDesc(WorkflowVersionPo::getId)
                .last("LIMIT 1"));
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流版本不存在");
        }
        return version;
    }

    private String normalizePublishType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase();
        if (!PUBLISH_TYPES.contains(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的工作流发布类型: " + value);
        }
        return type;
    }

    private int clampPercent(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, value));
    }

    private WorkflowPublishResp toResp(WorkflowPublishPo po) {
        WorkflowPublishResp resp = new WorkflowPublishResp();
        resp.setId(po.getId());
        resp.setWorkflowId(po.getWorkflowId());
        resp.setWorkflowVersionId(po.getWorkflowVersionId());
        resp.setPublishType(po.getPublishType());
        resp.setPublishStatus(po.getPublishStatus());
        resp.setEndpointKey(po.getEndpointKey());
        resp.setToolKey(po.getToolKey());
        resp.setDisplayName(po.getDisplayName());
        resp.setGrayPercent(po.getGrayPercent());
        resp.setPublishedAt(po.getPublishedAt());
        return resp;
    }
}
