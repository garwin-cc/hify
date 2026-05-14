# Hify Workflow Productionization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build backend productionization for workflow publish, version governance, runtime failure policy, run center, human review todos, CODE_TASK safety, variables, and limited triggers.

**Architecture:** Add focused services and DTOs around the existing modular monolith workflow module. Keep `WorkflowEngine` as the synchronous executor and only extend node-level runtime policy handling inside the execution loop.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway MySQL migration, Jackson, JUnit 5, AssertJ, Mockito.

---

### Task 1: Migration And Domain Models

**Files:**
- Create: `hify-app/src/main/resources/db/migration/V37__workflow_productionization.sql`
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowPublishPo.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowTriggerPo.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowVersionDiffPo.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/infra/WorkflowPublishMapper.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/infra/WorkflowTriggerMapper.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/infra/WorkflowVersionDiffMapper.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowVersionPo.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowRunPo.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowNodeRunPo.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowReviewTaskPo.java`

- [x] **Step 1: Write migration and PO fields**

Add tables and columns described in the design, using nullable fields or defaults so existing data stays valid.

- [x] **Step 2: Compile domain model**

Run: `mvn -pl hify-workflow -am -DskipTests compile`

Expected: compile reaches `hify-workflow` without missing mapper or field errors.

### Task 2: API DTOs And Service Contracts

**Files:**
- Modify: `hify-workflow/src/main/java/com/hify/workflow/api/WorkflowService.java`
- Create DTOs under `hify-workflow/src/main/java/com/hify/workflow/api/`:
  - `WorkflowPublishReq`
  - `WorkflowPublishResp`
  - `WorkflowRunQuery`
  - `WorkflowReviewQuery`
  - `WorkflowTriggerReq`
  - `WorkflowTriggerResp`
  - `WorkflowVariableResp`
  - `WorkflowVersionDiffResp`
  - `WorkflowRollbackReq`
- Modify existing response DTOs to include new production fields.

- [x] **Step 1: Add contract methods**

Add methods for publish list/create, version diff, rollback, run query, review query, trigger create/list, webhook trigger, and variable references.

- [x] **Step 2: Compile API**

Run: `mvn -pl hify-workflow -am -DskipTests compile`

Expected: interface and DTOs compile.

### Task 3: Publish, Version Diff, Rollback, And Variables

**Files:**
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowPublishService.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowVersionDiffService.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowVariableService.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowServiceImpl.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/web/WorkflowController.java`
- Test: `hify-workflow/src/test/java/com/hify/workflow/domain/WorkflowProductionServiceTest.java`

- [x] **Step 1: Write tests**

Cover:
- publishing binds a version and creates endpoint/tool keys.
- version diff detects node add/remove/change.
- rollback creates a new version from an old snapshot.
- variable references include `start.userMessage` and configured node output variables.

- [x] **Step 2: Implement services**

Use existing `WorkflowVersionMapper`, node config parser, and snapshot JSON patterns.

- [x] **Step 3: Run tests**

Run: `mvn -pl hify-workflow -Dtest=WorkflowProductionServiceTest test`

Expected: tests pass.

### Task 4: Runtime Policy In WorkflowEngine

**Files:**
- Create: `hify-workflow/src/main/java/com/hify/workflow/engine/NodeRuntimePolicy.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/engine/WorkflowEngine.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/config/NodeConfigParser.java`
- Test: `hify-workflow/src/test/java/com/hify/workflow/engine/WorkflowEngineRuntimePolicyTest.java`

- [x] **Step 1: Write tests**

Cover retry success, error branch routing, timeout failure, and HUMAN_REVIEW failure handoff.

- [x] **Step 2: Implement minimal runtime policy parser**

Read a `runtime` object from node config JSON without changing node-specific config records.

- [x] **Step 3: Wrap node execution**

Retry only node executor failures. Publish node events with attempt numbers. Preserve existing `HUMAN_REVIEW` WAITING behavior.

- [x] **Step 4: Run tests**

Run: `mvn -pl hify-workflow -Dtest=WorkflowEngineRuntimePolicyTest test`

Expected: tests pass.

### Task 5: Run Center, Human Review Todo, And Triggers

**Files:**
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowRunCenterService.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowTriggerService.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowReviewService.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowServiceImpl.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/web/WorkflowRunController.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/web/WorkflowTriggerController.java`
- Test: extend `WorkflowProductionServiceTest`

- [x] **Step 1: Write tests**

Cover run filtering, review todo filtering, webhook trigger run creation, and schedule trigger persistence.

- [x] **Step 2: Implement query services and controllers**

Use MyBatis-Plus wrapper queries and existing `PageHelper`.

- [x] **Step 3: Run workflow tests**

Run: `mvn -pl hify-workflow -am test`

Expected: all workflow and dependency tests pass.

### Task 6: CODE_TASK Safety And Final Verification

**Files:**
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/config/CodeTaskNodeConfig.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/engine/executor/CodeTaskConfig.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowServiceImpl.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/engine/executor/CodeTaskNodeExecutor.java`
- Test: `hify-workflow/src/test/java/com/hify/workflow/domain/config/NodeConfigParserTest.java`
- Test: extend production tests.

- [x] **Step 1: Write safety tests**

Cover non-MCP executor rejection, approval-required CODE_TASK without downstream HUMAN_REVIEW rejection, and safe config acceptance.

- [x] **Step 2: Implement config and graph validation**

Reject unsafe CODE_TASK at save and publish time.

- [x] **Step 3: Run final verification**

Run:
- `mvn -pl hify-workflow -am test`
- `mvn -pl hify-app -am -DskipTests compile`
- `git diff --check`

Expected: all pass.

## Self-Review

- Spec coverage: publish, version governance, runtime policy, run center, review todo, CODE_TASK safety, variables, and triggers are each mapped to tasks.
- Placeholder scan: no deferred placeholders remain in implementation steps.
- Type consistency: DTO and service names are consistent across tasks.
