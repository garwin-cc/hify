package com.hify.workflow.api;

import com.hify.common.web.PageResult;

public interface WorkflowTemplateService {

    PageResult<WorkflowTemplateListItemResp> listPage(WorkflowTemplateQuery query);

    WorkflowTemplateDetailResp getDetail(Long id);

    WorkflowDetailResp createWorkflow(Long templateId, CreateWorkflowFromTemplateReq req);

    WorkflowTemplateDetailResp createFromWorkflow(CreateTemplateFromWorkflowReq req);

    WorkflowTemplateExportResp exportTemplate(Long templateId, Long versionId);
}
