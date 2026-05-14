# Hify Workflow Productionization Design

## Background

Hify workflow already supports visual definition, async runs, event streaming, node debugging, version snapshots, human review, CODE_TASK through MCP Code Worker, and full-run rerun. The next step is not to rebuild it as a general automation platform. The goal is to make workflows safe and stable enough to publish and operate for internal Web App, API Endpoint, and controlled tool use.

## Scope

This design covers backend productionization for `hify-workflow`:

- Publish workflow versions as internal Web App, API Endpoint, or controlled tool.
- Extend version governance with draft, published, gray, rollback, and diff.
- Add node runtime policy for retry, timeout, error branch, and failure handoff.
- Improve the run center with searchable history, event replay, node inputs/outputs, and rerun.
- Improve human review with assignee, todo list, audit fields, due time, timeout action, and notification marker.
- Strengthen CODE_TASK safety while keeping the hard rule that Hify backend never executes shell locally.
- Provide variable reference metadata for the frontend variable panel.
- Add cautious triggers: webhook and schedule only.

Frontend editor work is intentionally limited to backend-ready DTOs and APIs. Full UI panels can be built after these contracts are stable.

## Architecture

Use incremental services around the existing `WorkflowServiceImpl` and `WorkflowEngine` instead of replacing them.

- `WorkflowPublishService` owns publish targets and endpoint/tool keys.
- `WorkflowVersionService` owns publish status, rollback metadata, and version diff summaries.
- `WorkflowRunCenterService` owns run listing and event replay queries.
- `WorkflowReviewService` continues to own review tasks and gains todo/timeout fields.
- `WorkflowVariableService` derives `{{nodeKey.varName}}` references from workflow nodes.
- `WorkflowTriggerService` owns webhook and schedule trigger metadata.
- `WorkflowEngine` gains node runtime policy handling for retry, timeout, and error branches.

Cross-module callers continue to use `hify-workflow/api` interfaces and DTOs.

## Data Model

Add `t_workflow_publish`:

- `workflow_id`, `workflow_version_id`, `publish_type`, `publish_status`
- `endpoint_key`, `tool_key`, `display_name`
- `gray_percent`, `published_by`, `published_at`

Add `t_workflow_trigger`:

- `workflow_id`, `workflow_version_id`, `trigger_type`
- `trigger_key`, `cron_expression`, `next_fire_at`, `enabled`
- `last_fire_at`, `last_run_id`

Add `t_workflow_version_diff`:

- `workflow_id`, `left_version_id`, `right_version_id`
- `summary_json`, `created_at`

Extend `t_workflow_version`:

- `version_status`: `DRAFT`, `PUBLISHED`, `GRAY`, `ARCHIVED`
- `parent_version_id`, `gray_percent`, `checksum`, `published_by`, `published_at`

Extend `t_workflow_run`:

- `trigger_type`, `trigger_id`, `publish_id`, `source`

Extend `t_workflow_node_run`:

- `attempt_no`, `max_attempts`, `timeout_seconds`, `failure_strategy`

Extend `t_workflow_review_task`:

- `assignee_user_id`, `assignee_username`, `due_at`, `timeout_action`
- `notified_at`, `expired_at`

All migrations follow existing MySQL conventions: bigint auto-increment id, `created_at`, `updated_at`, `deleted`, and composite indexes that include `deleted`.

## Runtime Policy

Each node may define runtime policy through its config:

```json
{
  "runtime": {
    "retryTimes": 2,
    "retryIntervalMs": 500,
    "timeoutSeconds": 60,
    "onFailure": "FAIL_RUN"
  }
}
```

Valid `onFailure` values:

- `FAIL_RUN`: existing behavior.
- `CONTINUE`: mark node failed but continue through default next edge.
- `ERROR_BRANCH`: route to an outgoing edge with `edgeType=ERROR` or `conditionExpression=ERROR`.
- `HUMAN_REVIEW`: pause run and create a review task for manual handling.

Runtime policy wraps one node execution only. It must not change `WAITING` / `RESUME` behavior for normal `HUMAN_REVIEW` nodes.

## Publish And Versioning

Saving a workflow continues to create a version snapshot. Publish explicitly binds one version to a target. Published runs must use the bound version id, so later draft edits do not affect production endpoints.

Rollback creates a new draft version from an old snapshot and can optionally publish it to replace the active publish target. Version diff produces a structured summary of changed workflow fields, added/removed/changed nodes, and added/removed/changed edges.

## Run Center

Run center adds a pageable query over `t_workflow_run` with filters:

- `workflowId`
- `status`
- `traceId`
- `runMode`
- `source`
- `createdAtStart` / `createdAtEnd`

Run detail continues to include node runs, call traces, input snapshots, outputs, error, and event stream recovery. Existing full-run rerun remains the first production rerun mode.

## Human Review

Review tasks support assignee, due time, timeout action, and notification marker. A todo list endpoint returns waiting or expired tasks by assignee/status/time range.

Timeout handling is initially conservative: a backend method marks expired waiting tasks and publishes events. Real message notification can be added later using the same fields.

## CODE_TASK Safety

CODE_TASK remains MCP-only. Config validation rejects non-MCP executors.

Additional safety fields:

- `sandboxRequired`
- `approvalRequired`
- `diffAuditRequired`
- `allowedProjectId`
- `allowedRole`

If `approvalRequired=true`, workflow save and publish validation require a downstream `HUMAN_REVIEW` node on the CODE_TASK path. CODE_TASK call trace stores sanitized request and response metadata including changed files and diff presence. Full local shell execution remains forbidden.

## Variables

The backend exposes workflow variable references derived from node definitions:

- `start.userMessage`
- node output variables from LLM, knowledge, API call, condition, human review, CODE_TASK, and END configs.
- standard error variables for runtime failure paths.

The response is designed for the frontend variable management panel and avoids runtime introspection.

## Trigger Boundary

Only two triggers are in scope:

- `WEBHOOK`: endpoint key invokes an async workflow run with request body as input.
- `SCHEDULE`: persisted schedule metadata and next fire time. A simple later scanner may execute it, but this design does not introduce a full automation engine.

No broad app event bus, file watcher, external connector marketplace, or n8n-style generic automation is included.

## Risks

- Engine changes can break WAITING/RESUME. Tests must cover existing human review resume behavior.
- Published endpoints must bind version id. Otherwise draft changes could leak into production.
- CODE_TASK safety must run at save and publish time, not only at execution time.
- Trigger scope can expand quickly. Keep webhook and schedule isolated in workflow module.
- Existing frontend DTO consumers need default values for all new fields.

## Verification

- Unit tests for publish, version diff, rollback, run center filtering, review todo fields, variable references, and CODE_TASK policy validation.
- Engine tests for retry, timeout, error branch, and human-review failure handoff.
- Compile app module with dependencies.
- Run `git diff --check`.
