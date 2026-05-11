# Workflow Template Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing lightweight workflow templates into an internal template library with immutable versions, workflow-to-template creation, export, and better preview metadata.

**Architecture:** Keep template logic inside `hify-workflow` and preserve the current `WorkflowTemplateService` boundary. Add version and usage persistence, continue creating real workflows through `WorkflowService`, and keep templates as non-executable snapshots.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway MySQL migrations, Vue 3, Element Plus, TypeScript.

---

### Task 1: Backend Template Version Core

**Files:**
- Create: `hify-app/src/main/resources/db/migration/V25__workflow_template_library.sql`
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowTemplateVersionPo.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowTemplateUsagePo.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/infra/WorkflowTemplateVersionMapper.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/infra/WorkflowTemplateUsageMapper.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowTemplatePo.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowPo.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/api/WorkflowTemplateListItemResp.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/api/WorkflowTemplateDetailResp.java`
- Test: `hify-workflow/src/test/java/com/hify/workflow/domain/WorkflowTemplateServiceImplTest.java`

- [ ] **Step 1: Write failing test**

Add a test that creates a template with `currentVersionId`, has a version row, calls `getDetail`, and expects `currentVersionId`, `latestVersionNo`, `status`, `nodeTypes`, and requirements from the version snapshot.

- [ ] **Step 2: Run failing test**

Run: `mvn -pl hify-workflow test -Dtest=WorkflowTemplateServiceImplTest`

- [ ] **Step 3: Implement minimal persistence and response fields**

Add migration, PO fields, mappers, DTO fields, and make detail/list read version snapshots when `currentVersionId` is set.

- [ ] **Step 4: Run passing test**

Run: `mvn -pl hify-workflow test -Dtest=WorkflowTemplateServiceImplTest`

### Task 2: Create Template From Workflow

**Files:**
- Create: `hify-workflow/src/main/java/com/hify/workflow/api/CreateTemplateFromWorkflowReq.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/api/WorkflowTemplateVersionResp.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/api/WorkflowTemplateService.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowTemplateServiceImpl.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/web/WorkflowTemplateController.java`
- Test: `hify-workflow/src/test/java/com/hify/workflow/domain/WorkflowTemplateServiceImplTest.java`

- [ ] **Step 1: Write failing test**

Add a test that loads an existing workflow detail, calls `createFromWorkflow`, and verifies a template plus version are created with model/knowledge/tool IDs replaced by placeholders.

- [ ] **Step 2: Run failing test**

Run: `mvn -pl hify-workflow test -Dtest=WorkflowTemplateServiceImplTest`

- [ ] **Step 3: Implement snapshot builder**

Read workflow detail through `WorkflowService`, build template JSON, replace resource IDs with `modelConfigRef`, `knowledgeBaseRef`, and `toolRef`, and create `DRAFT` or `PUBLISHED` template version.

- [ ] **Step 4: Run passing test**

Run: `mvn -pl hify-workflow test -Dtest=WorkflowTemplateServiceImplTest`

### Task 3: Export and Usage Tracking

**Files:**
- Create: `hify-workflow/src/main/java/com/hify/workflow/api/WorkflowTemplateExportResp.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/api/WorkflowTemplateService.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/domain/WorkflowTemplateServiceImpl.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/web/WorkflowTemplateController.java`
- Test: `hify-workflow/src/test/java/com/hify/workflow/domain/WorkflowTemplateServiceImplTest.java`

- [ ] **Step 1: Write failing test**

Add a test that exports a template version and expects a sanitized JSON payload plus a usage record with `EXPORT`.

- [ ] **Step 2: Run failing test**

Run: `mvn -pl hify-workflow test -Dtest=WorkflowTemplateServiceImplTest`

- [ ] **Step 3: Implement export and usage**

Return the version snapshot JSON as `WorkflowTemplateExportResp`, write usage rows for `USE` and `EXPORT`, and increment template usage count when a workflow is created.

- [ ] **Step 4: Run passing test**

Run: `mvn -pl hify-workflow test -Dtest=WorkflowTemplateServiceImplTest`

### Task 4: Frontend Template Library UX

**Files:**
- Modify: `hify-web/src/api/workflow.ts`
- Modify: `hify-web/src/views/workflow/WorkflowTemplateView.vue`
- Modify: `hify-web/src/views/workflow/WorkflowTemplateCreateView.vue`
- Modify: `hify-web/src/views/workflow/WorkflowCreateView.vue`

- [ ] **Step 1: Add API types and calls**

Expose template version/status/tags/nodeTypes, `createTemplateFromWorkflow`, and `exportWorkflowTemplate`.

- [ ] **Step 2: Enhance template list/detail experience**

Show status, version, usage count, tags, node type summary, and replace JSON-only preview with a compact read-only node/edge preview in the create page.

- [ ] **Step 3: Add save-as-template action**

Add an editor action that opens a dialog for name, description, category, tags, status, and changelog, then calls `createTemplateFromWorkflow`.

- [ ] **Step 4: Build frontend**

Run: `npm run build` in `hify-web`.

### Task 5: Final Verification

**Files:** all modified files.

- [ ] **Step 1: Run backend targeted tests**

Run: `mvn -pl hify-workflow test -Dtest=WorkflowTemplateServiceImplTest`

- [ ] **Step 2: Run app build/test**

Run: `mvn -pl hify-app -am test`

- [ ] **Step 3: Run frontend build**

Run: `npm run build` in `hify-web`.
