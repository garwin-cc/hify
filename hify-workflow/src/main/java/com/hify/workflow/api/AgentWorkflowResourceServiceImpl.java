package com.hify.workflow.api;

import com.hify.agent.api.AgentResourceRef;
import com.hify.agent.api.AgentWorkflowResourceService;
import com.hify.workflow.domain.WorkflowPo;
import com.hify.workflow.infra.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentWorkflowResourceServiceImpl implements AgentWorkflowResourceService {

    private final WorkflowMapper workflowMapper;

    @Override
    public AgentResourceRef getWorkflowResource(Long workflowId) {
        if (workflowId == null) {
            return null;
        }
        WorkflowPo po = workflowMapper.selectById(workflowId);
        if (po == null) {
            return null;
        }
        return new AgentResourceRef(po.getId(), po.getWorkspaceId(), po.getProjectId(), po.getEnabled());
    }
}
