# Hify MCP Security And OpenAPI Tooling Design

## Background

Hify already has MCP Server CRUD, tool synchronization, MCP SDK/raw HTTP fallback, basic tool-call audit, and tool governance fields such as `dangerous`, `permissionLevel`, and `schemaValidationEnabled`. The missing production pieces are project isolation, SSRF protection, parameter validation, Secret references, OpenAPI tool source support, and searchable audit.

## Scope

This design covers backend productionization for `hify-mcp`:

- Project and workspace ownership for MCP Servers and tools.
- Endpoint SSRF guard with allowlist, protocol, port, and private-network policy.
- Tool-call audit query by source, Agent, Workflow, user, tool, status, latency, traceId, and time range.
- Tool argument schema validation for common JSON Schema fields.
- Tool timeout, retry, and fallback strategy.
- OpenAPI tool source ingestion as a lightweight REST API tool option.
- Secret management through credential references instead of embedding plaintext credentials in node JSON.

The goal is not to build a full API gateway or plugin marketplace.

## Architecture

Use a unified tool gateway inside `hify-mcp`:

- `McpEndpointGuard` validates MCP and OpenAPI endpoints before save, test, sync, and call.
- `McpToolSchemaValidator` validates arguments against stored tool schema.
- `ToolSecretService` creates and resolves encrypted credential references.
- `OpenApiToolService` stores OpenAPI sources and syncs operations into OpenAPI tool records.
- `McpToolCallAuditServiceImpl` remains the audit writer and gains query APIs.
- `McpClientServiceImpl` remains the MCP call entry and gains policy enforcement.

Existing `McpService` and `McpClientService` contracts remain compatible. New context-aware methods are added for Agent/Workflow/Conversation callers that can provide project and source metadata.

## Data Model

Extend `t_mcp_server`:

- `workspace_id`, `project_id`
- `visibility`: `PROJECT`, `WORKSPACE`, `PUBLIC`
- `share_scope`: `PRIVATE`, `SHARED`
- `secret_id`
- `connect_timeout_ms`, `read_timeout_ms`, `retry_times`, `retry_interval_ms`
- `fallback_strategy`: `FAIL_FAST`, `RETURN_ERROR_MESSAGE`

Extend `t_mcp_tool`:

- `tool_type`: `MCP`, `OPENAPI`
- `openapi_tool_id`
- `timeout_ms`, `retry_times`, `fallback_strategy`

Extend `t_mcp_tool_call_audit`:

- `workspace_id`, `project_id`, `agent_id`, `app_id`, `api_key_id`, `user_id`, `workflow_id`
- `status`, `retry_count`, `timeout_ms`, `source_id`

Add `t_tool_secret`:

- project ownership, name, secret type, key prefix, encrypted value, status.

Add `t_openapi_tool_source`:

- project ownership, endpoint, spec JSON, secret reference, SSRF policy, status.

Add `t_openapi_tool`:

- parsed operation metadata, method, path, parameter schema, response schema.

## Endpoint Guard

Default policy:

- Allow `https`.
- Allow `http` only when `hify.mcp.security.allow-http=true`.
- Reject unsupported schemes.
- Reject localhost, loopback, link-local, and metadata IPs.
- Reject private network CIDRs unless `hify.mcp.security.allow-private-network=true` or host matches allowlist.
- Allow ports `443`, and optionally configured ports.
- Normalize endpoint by trimming whitespace and parsing as URI, not by string concatenation.

The guard returns explicit business errors so admins know whether protocol, host, or port was rejected.

## Permission Model

MCP Server and OpenAPI source access follows project ownership:

- `PROJECT`: only same project.
- `WORKSPACE`: same workspace.
- `PUBLIC`: visible across workspace boundary only to admin or explicit shared scope.

Existing calls that do not pass project context remain compatible but new Agent/Workflow binding paths should pass projectId. Dangerous tools require RUN permission and are not available to viewers.

## Schema Validation

Support a pragmatic JSON Schema subset:

- root `type=object`
- `required`
- `properties`
- primitive `type`: string, number, integer, boolean, object, array
- `enum`
- `additionalProperties=false`

On validation failure, the call is rejected before hitting the external tool and audit is recorded with failure status.

## Retry And Fallback

Retry only network exceptions, timeouts, and 5xx-style failures from raw HTTP fallback. Do not retry schema failures or business errors.

Fallback strategies:

- `FAIL_FAST`: throw `BizException`.
- `RETURN_ERROR_MESSAGE`: return a controlled error string so conversation/tool-message chains can continue.

## Secret Management

`ToolSecretService` stores encrypted values with AES-GCM using `hify.tool-secret.master-key`. If no master key is configured, creating Secret is rejected.

Responses return only id, name, secret type, key prefix, status, and timestamps. Plain secret value is never returned.

## OpenAPI Tools

OpenAPI support is intentionally small:

- Accept JSON OpenAPI 3.x spec.
- Parse `paths` operations into tools.
- Tool name defaults to `operationId`, falling back to `method_path`.
- Request body JSON schema is used as input schema.
- Tool invocation sends JSON request body to source endpoint + path.

OAuth2 and multipart upload are out of scope.

## Risks

- SSRF guard may block existing internal MCP endpoints. Allowlist and private-network flag are required for controlled exceptions.
- Permission checks can break existing Agent bindings if project context is missing. Existing no-context APIs stay compatible; new project-aware APIs enforce strictly.
- Full JSON Schema support is intentionally not included.
- Secret encryption depends on master key persistence.
- OpenAPI parsing must remain limited to avoid becoming a full gateway.

## Verification

- Unit tests for endpoint guard allow/reject cases.
- Unit tests for schema validation.
- Unit tests for Secret encryption response behavior.
- Unit tests for audit query and field mapping.
- Compile `hify-mcp` and `hify-app`.
- Run `git diff --check`.
