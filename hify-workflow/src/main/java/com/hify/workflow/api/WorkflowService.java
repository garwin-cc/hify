package com.hify.workflow.api;

import com.hify.common.web.PageResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 工作流服务，跨模块调用的统一入口。 */
public interface WorkflowService {

    WorkflowDetailResp create(CreateWorkflowReq req);

    PageResult<WorkflowListItemResp> listPage(WorkflowQuery query);

    WorkflowDetailResp getDetail(Long id);

    WorkflowDetailResp update(Long id, UpdateWorkflowReq req);

    void delete(Long id);

    WorkflowRunResp run(Long id, WorkflowRunReq req);

    WorkflowRunResp startAsyncRun(Long id, WorkflowRunReq req);

    WorkflowRunResp getRunDetail(Long runId);

    PageResult<WorkflowRunResp> listRuns(WorkflowRunQuery query);

    WorkflowRunResp rerunRun(Long runId);

    SseEmitter streamRunEvents(Long runId, Integer afterEventSeq);

    WorkflowReviewTaskResp getReviewTask(Long runId);

    PageResult<WorkflowReviewTaskResp> listReviewTasks(WorkflowReviewQuery query);

    WorkflowRunResp submitReview(Long runId, SubmitWorkflowReviewReq req);

    WorkflowRunResp getLatestRun(Long id);

    WorkflowNodeDebugResp debugNode(Long workflowId, String nodeKey, WorkflowNodeDebugReq req);

    List<WorkflowVersionResp> listVersions(Long workflowId);

    WorkflowVersionResp getVersion(Long workflowId, Integer versionNo);

    WorkflowVersionDiffResp diffVersions(Long workflowId, Integer leftVersionNo, Integer rightVersionNo);

    WorkflowDetailResp restoreVersion(Long workflowId, Integer versionNo);

    WorkflowDetailResp rollbackVersion(Long workflowId, Integer versionNo, WorkflowRollbackReq req);

    WorkflowPublishResp publish(Long workflowId, WorkflowPublishReq req);

    List<WorkflowPublishResp> listPublishes(Long workflowId);

    WorkflowTriggerResp createTrigger(Long workflowId, WorkflowTriggerReq req);

    List<WorkflowTriggerResp> listTriggers(Long workflowId);

    WorkflowRunResp triggerWebhook(String triggerKey, WorkflowRunReq req);

    List<WorkflowVariableResp> listVariables(Long workflowId);
}
