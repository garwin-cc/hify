# Stabilization Route 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the first stabilization route real: LLM resilience, workflow event visibility in chat, and MCP tool-call auditing.

**Architecture:** Keep the current modular monolith boundaries. LLM resilience stays in `hify-model`/`hify-common`; MCP audit storage stays in `hify-mcp` and is exposed through an api interface; conversation and workflow code pass contextual metadata when calling MCP tools. The frontend only adds workflow-run event subscription and concise progress rendering to the existing chat view.

**Tech Stack:** Spring Boot 3.2, MyBatis-Plus, Flyway, JUnit 5/Mockito, Vue 3, TypeScript, Element Plus, fetch/EventSource SSE.

---

### Task 1: LLM Resilience

**Files:**
- Modify: `hify-common/src/main/java/com/hify/common/http/LlmHttpClient.java`
- Modify: `hify-model/src/main/java/com/hify/model/domain/LlmCallServiceImpl.java`
- Modify: `hify-app/src/main/resources/application.yml`
- Test: `hify-model/src/test/java/com/hify/model/domain/LlmCallServiceImplTest.java`

- [ ] Write failing tests proving `LlmCallServiceImpl` invokes the circuit breaker and falls back to the configured provider type when the primary stream fails.
- [ ] Implement `hify.llm.fallback` config binding and model lookup for enabled fallback chat models.
- [ ] Wrap chat and stream calls in `CircuitBreakerService`.
- [ ] Set stream read timeout to zero.
- [ ] Run the targeted model tests.

### Task 2: MCP Tool Audit

**Files:**
- Create: `hify-mcp/src/main/java/com/hify/mcp/api/McpToolCallAuditService.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/api/McpToolCallAuditRecord.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/McpToolCallAuditServiceImpl.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/McpToolCallAuditPo.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/infra/McpToolCallAuditMapper.java`
- Create: `hify-app/src/main/resources/db/migration/V23__mcp_tool_call_audit.sql`
- Modify: `hify-conversation/src/main/java/com/hify/conversation/domain/ConversationServiceImpl.java`
- Modify: `hify-workflow/src/main/java/com/hify/workflow/engine/executor/McpCodeTaskRunner.java`
- Test: `hify-mcp/src/test/java/com/hify/mcp/domain/McpToolCallAuditServiceImplTest.java`

- [ ] Write failing audit tests proving only argument keys and output/error summaries are persisted.
- [ ] Add Flyway table and MyBatis mapper.
- [ ] Record conversation tool calls with conversation/session/message context.
- [ ] Record CODE_TASK MCP calls with workflow run and node context.
- [ ] Run targeted MCP and conversation tests.

### Task 3: Workflow Events in Chat

**Files:**
- Modify: `hify-conversation/src/main/java/com/hify/conversation/domain/ConversationServiceImpl.java`
- Modify: `hify-web/src/api/conversation.ts`
- Modify: `hify-web/src/views/conversation/ConversationView.vue`

- [ ] Add a workflow-start SSE event containing `workflowRunId`.
- [ ] Extend frontend stream parsing with workflow-start events.
- [ ] Subscribe to `/api/v1/workflow-runs/{id}/events` from the chat page after workflow-start.
- [ ] Render concise workflow event progress and final output/error in the assistant bubble.
- [ ] Close both POST stream and workflow EventSource on component unmount or session switch.

### Task 4: Verification

- [ ] Run targeted backend Maven tests for changed modules.
- [ ] Run frontend typecheck/build.
- [ ] Review git diff for unrelated changes and summarize only this batch.
