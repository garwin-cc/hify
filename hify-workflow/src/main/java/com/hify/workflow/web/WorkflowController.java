package com.hify.workflow.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.workflow.api.CreateWorkflowReq;
import com.hify.workflow.api.UpdateWorkflowReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowListItemResp;
import com.hify.workflow.api.WorkflowNodeDebugReq;
import com.hify.workflow.api.WorkflowNodeDebugResp;
import com.hify.workflow.api.WorkflowPublishReq;
import com.hify.workflow.api.WorkflowPublishResp;
import com.hify.workflow.api.WorkflowQuery;
import com.hify.workflow.api.WorkflowRollbackReq;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowTriggerReq;
import com.hify.workflow.api.WorkflowTriggerResp;
import com.hify.workflow.api.WorkflowVariableResp;
import com.hify.workflow.api.WorkflowVersionDiffResp;
import com.hify.workflow.api.WorkflowVersionResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowDetailResp> create(@Valid @RequestBody CreateWorkflowReq req) {
        return Result.ok(workflowService.create(req));
    }

    @GetMapping
    public PageResult<WorkflowListItemResp> list(WorkflowQuery query) {
        return workflowService.listPage(query);
    }

    @GetMapping("/{id}")
    public Result<WorkflowDetailResp> getDetail(@PathVariable Long id) {
        return Result.ok(workflowService.getDetail(id));
    }

    @PutMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowDetailResp> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateWorkflowReq req) {
        return Result.ok(workflowService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/run")
    public Result<WorkflowRunResp> run(@PathVariable Long id,
                                       @Valid @RequestBody WorkflowRunReq req) {
        return Result.ok(workflowService.run(id, req));
    }

    @PostMapping("/{id}/runs")
    public Result<WorkflowRunResp> startAsyncRun(@PathVariable Long id,
                                                 @Valid @RequestBody WorkflowRunReq req) {
        return Result.ok(workflowService.startAsyncRun(id, req));
    }

    @GetMapping("/{id}/runs/latest")
    public Result<WorkflowRunResp> getLatestRun(@PathVariable Long id) {
        return Result.ok(workflowService.getLatestRun(id));
    }

    @PostMapping("/{id}/nodes/{nodeKey}/debug")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowNodeDebugResp> debugNode(@PathVariable Long id,
                                                   @PathVariable String nodeKey,
                                                   @RequestBody WorkflowNodeDebugReq req) {
        return Result.ok(workflowService.debugNode(id, nodeKey, req));
    }

    @GetMapping("/{id}/versions")
    public Result<List<WorkflowVersionResp>> listVersions(@PathVariable Long id) {
        return Result.ok(workflowService.listVersions(id));
    }

    @GetMapping("/{id}/versions/{versionNo}")
    public Result<WorkflowVersionResp> getVersion(@PathVariable Long id,
                                                  @PathVariable Integer versionNo) {
        return Result.ok(workflowService.getVersion(id, versionNo));
    }

    @GetMapping("/{id}/versions/{leftVersionNo}/diff/{rightVersionNo}")
    public Result<WorkflowVersionDiffResp> diffVersions(@PathVariable Long id,
                                                        @PathVariable Integer leftVersionNo,
                                                        @PathVariable Integer rightVersionNo) {
        return Result.ok(workflowService.diffVersions(id, leftVersionNo, rightVersionNo));
    }

    @PostMapping("/{id}/versions/{versionNo}/restore")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowDetailResp> restoreVersion(@PathVariable Long id,
                                                     @PathVariable Integer versionNo) {
        return Result.ok(workflowService.restoreVersion(id, versionNo));
    }

    @PostMapping("/{id}/versions/{versionNo}/rollback")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowDetailResp> rollbackVersion(@PathVariable Long id,
                                                      @PathVariable Integer versionNo,
                                                      @RequestBody(required = false) WorkflowRollbackReq req) {
        return Result.ok(workflowService.rollbackVersion(id, versionNo, req == null ? new WorkflowRollbackReq() : req));
    }

    @PostMapping("/{id}/publish")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowPublishResp> publish(@PathVariable Long id,
                                               @Valid @RequestBody WorkflowPublishReq req) {
        return Result.ok(workflowService.publish(id, req));
    }

    @GetMapping("/{id}/publishes")
    public Result<List<WorkflowPublishResp>> listPublishes(@PathVariable Long id) {
        return Result.ok(workflowService.listPublishes(id));
    }

    @PostMapping("/{id}/triggers")
    @RequireRole({UserRole.ADMIN, UserRole.EDITOR})
    public Result<WorkflowTriggerResp> createTrigger(@PathVariable Long id,
                                                     @Valid @RequestBody WorkflowTriggerReq req) {
        return Result.ok(workflowService.createTrigger(id, req));
    }

    @GetMapping("/{id}/triggers")
    public Result<List<WorkflowTriggerResp>> listTriggers(@PathVariable Long id) {
        return Result.ok(workflowService.listTriggers(id));
    }

    @GetMapping("/{id}/variables")
    public Result<List<WorkflowVariableResp>> listVariables(@PathVariable Long id) {
        return Result.ok(workflowService.listVariables(id));
    }

}
