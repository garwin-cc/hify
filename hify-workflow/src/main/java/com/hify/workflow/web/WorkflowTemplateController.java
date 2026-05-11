package com.hify.workflow.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.workflow.api.CreateTemplateFromWorkflowReq;
import com.hify.workflow.api.CreateWorkflowFromTemplateReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowTemplateDetailResp;
import com.hify.workflow.api.WorkflowTemplateExportResp;
import com.hify.workflow.api.WorkflowTemplateListItemResp;
import com.hify.workflow.api.WorkflowTemplateQuery;
import com.hify.workflow.api.WorkflowTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-templates")
@RequiredArgsConstructor
public class WorkflowTemplateController {

    private final WorkflowTemplateService workflowTemplateService;

    @GetMapping
    public PageResult<WorkflowTemplateListItemResp> list(WorkflowTemplateQuery query) {
        return workflowTemplateService.listPage(query);
    }

    @GetMapping("/{id}")
    public Result<WorkflowTemplateDetailResp> getDetail(@PathVariable Long id) {
        return Result.ok(workflowTemplateService.getDetail(id));
    }

    @PostMapping("/{id}/create-workflow")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowDetailResp> createWorkflow(@PathVariable Long id,
                                                     @Valid @RequestBody CreateWorkflowFromTemplateReq req) {
        return Result.ok(workflowTemplateService.createWorkflow(id, req));
    }

    @PostMapping("/from-workflow")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowTemplateDetailResp> createFromWorkflow(@Valid @RequestBody CreateTemplateFromWorkflowReq req) {
        return Result.ok(workflowTemplateService.createFromWorkflow(req));
    }

    @GetMapping("/{id}/versions/{versionId}/export")
    public Result<WorkflowTemplateExportResp> exportTemplate(@PathVariable Long id,
                                                             @PathVariable Long versionId) {
        return Result.ok(workflowTemplateService.exportTemplate(id, versionId));
    }
}
