# Hify Agent Release Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the backend loop for Agent versioning, publishing, debugging metadata, tool governance, and project-safe resource binding.

**Architecture:** Keep Agent ownership in `hify-agent`; expose workflow resource metadata through a narrow `hify-agent/api` interface implemented by `hify-workflow` to avoid circular dependencies. Reuse existing conversation trace and MCP audit paths instead of adding a parallel debug log system.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway SQL migrations, JUnit 5, Mockito, MySQL JSON columns.

---

### Task 1: Schema And DTOs

**Files:**
- Create: `hify-app/src/main/resources/db/migration/V34__agent_release_governance.sql`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentVersionResp.java`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentPublishReq.java`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentRollbackReq.java`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentAppReq.java`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentAppResp.java`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentApiKeyResp.java`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentApiKeyCreateResp.java`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentResourceRef.java`
- Create: `hify-agent/src/main/java/com/hify/agent/api/AgentWorkflowResourceService.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/api/CreateAgentReq.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/api/UpdateAgentReq.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/api/AgentDetailResp.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/api/AgentListItemResp.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/api/KnowledgeBaseResp.java`
- Modify: `hify-mcp/src/main/java/com/hify/mcp/api/McpToolResp.java`
- Modify: `hify-conversation/src/main/java/com/hify/conversation/api/ConversationTraceDetailResp.java`

- [ ] Add migration with `t_agent_version`, `t_agent_app`, `t_agent_api_key`, `t_agent.max_tool_rounds`, agent version columns, MCP tool governance columns, and conversation debug columns.
- [ ] Add DTOs with only API-layer types.
- [ ] Add `projectId/workspaceId` to knowledge/tool response DTOs for permission checks.
- [ ] Add `maxToolRounds` to Agent create/update/detail/list DTOs.

### Task 2: Agent Version Service

**Files:**
- Create: `hify-agent/src/main/java/com/hify/agent/domain/AgentVersionPo.java`
- Create: `hify-agent/src/main/java/com/hify/agent/infra/AgentVersionMapper.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/api/AgentService.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/domain/AgentServiceImpl.java`
- Test: `hify-agent/src/test/java/com/hify/agent/domain/AgentReleaseGovernanceServiceTest.java`

- [ ] Write failing tests for create draft, publish, rollback.
- [ ] Implement snapshot creation from `AgentPo` and bound tool IDs.
- [ ] Create v1 draft on Agent creation.
- [ ] Create new draft on update.
- [ ] Publish `TEST` and `PUBLISHED` immutable versions.
- [ ] Roll back by restoring a version snapshot and creating a new draft.

### Task 3: Agent App And API Key Management

**Files:**
- Create: `hify-agent/src/main/java/com/hify/agent/domain/AgentAppPo.java`
- Create: `hify-agent/src/main/java/com/hify/agent/domain/AgentApiKeyPo.java`
- Create: `hify-agent/src/main/java/com/hify/agent/infra/AgentAppMapper.java`
- Create: `hify-agent/src/main/java/com/hify/agent/infra/AgentApiKeyMapper.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/api/AgentService.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/domain/AgentServiceImpl.java`
- Modify: `hify-agent/src/main/java/com/hify/agent/web/AgentController.java`
- Create: `hify-agent/src/main/java/com/hify/agent/web/AgentAppController.java`
- Test: `hify-agent/src/test/java/com/hify/agent/domain/AgentReleaseGovernanceServiceTest.java`

- [ ] Write failing tests for creating app from published version and rejecting non-published versions.
- [ ] Implement app create/list.
- [ ] Implement API key creation with one-time plaintext response and stored SHA-256 hash.
- [ ] Implement API key list and revoke.

### Task 4: Resource Permission And Tool Governance

**Files:**
- Modify: `hify-agent/src/main/java/com/hify/agent/domain/AgentServiceImpl.java`
- Modify: `hify-mcp/src/main/java/com/hify/mcp/domain/McpToolPo.java`
- Modify: `hify-mcp/src/main/java/com/hify/mcp/domain/McpServiceImpl.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeServiceImpl.java`
- Create: `hify-workflow/src/main/java/com/hify/workflow/api/AgentWorkflowResourceServiceImpl.java`
- Test: `hify-agent/src/test/java/com/hify/agent/domain/AgentResourcePermissionTest.java`

- [ ] Write failing tests for cross-project resource bind rejection.
- [ ] Validate knowledge base project through `KnowledgeService`.
- [ ] Validate MCP tool project and dangerous permission through `McpService`.
- [ ] Validate workflow project through `AgentWorkflowResourceService`.
- [ ] Record audit for publish, rollback, app publish, and API key changes.

### Task 5: Conversation Runtime Governance

**Files:**
- Create: `hify-conversation/src/main/java/com/hify/conversation/domain/ToolArgumentSchemaValidator.java`
- Modify: `hify-conversation/src/main/java/com/hify/conversation/domain/ConversationServiceImpl.java`
- Modify: `hify-conversation/src/main/java/com/hify/conversation/infra/ConversationTracePo.java`
- Modify: `hify-conversation/src/main/java/com/hify/conversation/infra/ConversationLlmTracePo.java`
- Test: `hify-conversation/src/test/java/com/hify/conversation/domain/ToolArgumentSchemaValidatorTest.java`

- [ ] Write failing tests for required argument and type validation.
- [ ] Use `agent.maxToolRounds` in recursive tool execution.
- [ ] Validate tool arguments before calling MCP.
- [ ] Persist system prompt and LLM request summary in conversation trace.

### Task 6: Verification

- [ ] Run `mvn -pl hify-agent -am test`.
- [ ] Run `mvn -pl hify-conversation -am test`.
- [ ] Run `mvn -pl hify-app -am -DskipTests compile`.
- [ ] Confirm `git status --short` has only expected files.
