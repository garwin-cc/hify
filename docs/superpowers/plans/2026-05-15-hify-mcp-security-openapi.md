# Hify MCP Security And OpenAPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add MCP project isolation, SSRF protection, tool schema validation, searchable audit, Secret references, retry/fallback policy, and lightweight OpenAPI tool support.

**Architecture:** Keep existing `McpServiceImpl` and `McpClientServiceImpl` as primary entry points, and add focused helper services for endpoint security, schema validation, Secret handling, OpenAPI sync, and audit query.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway MySQL migration, Jackson, OkHttp, JUnit 5, AssertJ, Mockito.

---

### Task 1: Schema And Domain Model

**Files:**
- Create: `hify-app/src/main/resources/db/migration/V38__mcp_security_openapi_secret.sql`
- Modify: `hify-mcp/src/main/java/com/hify/mcp/domain/McpServerPo.java`
- Modify: `hify-mcp/src/main/java/com/hify/mcp/domain/McpToolPo.java`
- Modify: `hify-mcp/src/main/java/com/hify/mcp/domain/McpToolCallAuditPo.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/ToolSecretPo.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/OpenApiToolSourcePo.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/OpenApiToolPo.java`
- Create Mapper classes for the new PO files.

- [ ] **Step 1: Add migration with compatible defaults**

Existing MCP rows must remain valid. New ownership fields default to workspace/project `1`, visibility `PROJECT`, and share scope `PRIVATE`.

- [ ] **Step 2: Add PO and mapper fields**

Use MyBatis-Plus `BaseMapper`; use Jackson type handlers for JSON columns.

### Task 2: Endpoint Guard And Schema Validator

**Files:**
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/McpEndpointGuard.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/McpToolSchemaValidator.java`
- Test: `hify-mcp/src/test/java/com/hify/mcp/domain/McpEndpointGuardTest.java`
- Test: `hify-mcp/src/test/java/com/hify/mcp/domain/McpToolSchemaValidatorTest.java`

- [ ] **Step 1: Write failing guard tests**

Cover `https` allow, `file://` reject, localhost reject, metadata IP reject, private network default reject, and allowlist allow.

- [ ] **Step 2: Implement guard**

Parse endpoint as URI and reject unsupported schemes/hosts/ports before any network call.

- [ ] **Step 3: Write failing schema tests**

Cover missing required field, wrong primitive type, enum mismatch, and valid object.

- [ ] **Step 4: Implement validator**

Support JSON Schema subset from the design.

### Task 3: DTOs, Secret Service, And OpenAPI Service

**Files:**
- Add API DTOs under `hify-mcp/src/main/java/com/hify/mcp/api/`.
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/ToolSecretService.java`
- Create: `hify-mcp/src/main/java/com/hify/mcp/domain/OpenApiToolService.java`
- Create controllers for secrets, OpenAPI sources, and audit query.

- [ ] **Step 1: Add DTOs**

Add request/response/query DTOs for Secret, OpenAPI source, OpenAPI tool, and audit query.

- [ ] **Step 2: Implement Secret service**

Reject creation when master key is missing. Encrypt with AES-GCM and return only metadata.

- [ ] **Step 3: Implement OpenAPI source sync**

Parse OpenAPI 3 JSON paths into tools using `operationId` or method/path fallback names.

### Task 4: Integrate MCP Service And Client Calls

**Files:**
- Modify: `McpServiceImpl`
- Modify: `McpClientServiceImpl`
- Modify: `McpRawHttpClient`
- Modify: `McpToolCallAuditServiceImpl`

- [ ] **Step 1: Guard endpoint on create/update/test/call**

Every external endpoint path must pass `McpEndpointGuard`.

- [ ] **Step 2: Enforce project-aware access where context exists**

Add context-aware APIs while preserving existing no-context methods.

- [ ] **Step 3: Validate arguments before tool call**

Load tool schema by server/tool name and validate when enabled.

- [ ] **Step 4: Apply timeout/retry/fallback strategy**

Use server/tool policy defaults. Audit retry count and timeout.

### Task 5: Verification

**Files:**
- Tests under `hify-mcp/src/test/java/com/hify/mcp/domain/`

- [ ] **Step 1: Run module tests**

Run: `mvn -pl hify-mcp -am test`

- [ ] **Step 2: Compile app**

Run: `mvn -pl hify-app -am -DskipTests compile`

- [ ] **Step 3: Check diff**

Run: `git diff --check`

## Self-Review

- Spec coverage: project isolation, SSRF, audit query, schema validation, retry/fallback, OpenAPI, and Secret management each have implementation tasks.
- Placeholder scan: no task is intentionally deferred except out-of-scope OAuth2/full JSON Schema.
- Type consistency: service and DTO names match the implementation plan.
