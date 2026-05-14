# Hify Model Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a backend-only hify-model governance loop for provider health alerts, LLM statistics, default model policy, fallback priority, and OpenAI-compatible provider hardening.

**Architecture:** Keep the implementation inside `hify-model` plus Flyway migrations. `LlmCallServiceImpl` remains the runtime entry point and delegates statistics and default policy concerns to focused domain services.

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL/Flyway, JUnit 5, Mockito.

---

### Task 1: Provider Health Alert Fields

**Files:**
- Create: `hify-app/src/main/resources/db/migration/V33__model_governance.sql`
- Modify: `hify-model/src/main/java/com/hify/model/infra/ProviderHealthPo.java`
- Modify: `hify-model/src/main/java/com/hify/model/api/ProviderHealthResp.java`
- Modify: `hify-model/src/main/java/com/hify/model/domain/ProviderServiceImpl.java`
- Modify: `hify-model/src/main/java/com/hify/model/domain/ProviderHealthCheckJob.java`

- [ ] Add `last_error_at`, `last_alert_at`, `alert_status`, `success_count`, `total_check_count` to `t_provider_health`.
- [ ] Add matching PO/response fields.
- [ ] Update provider detail/list mapping so health data is visible through existing APIs.
- [ ] Update health check writes so consecutive failures mark `alert_status=OPEN`; success resets it to `OK`.

### Task 2: Default Model Policy

**Files:**
- Create: `hify-model/src/main/java/com/hify/model/api/ModelDefaultPolicyReq.java`
- Create: `hify-model/src/main/java/com/hify/model/api/ModelDefaultPolicyResp.java`
- Create: `hify-model/src/main/java/com/hify/model/api/ModelDefaultPolicyService.java`
- Create: `hify-model/src/main/java/com/hify/model/domain/ModelDefaultPolicyPo.java`
- Create: `hify-model/src/main/java/com/hify/model/domain/ModelDefaultPolicyServiceImpl.java`
- Create: `hify-model/src/main/java/com/hify/model/infra/ModelDefaultPolicyMapper.java`
- Create: `hify-model/src/main/java/com/hify/model/web/ModelDefaultPolicyController.java`
- Test: `hify-model/src/test/java/com/hify/model/domain/ModelDefaultPolicyServiceImplTest.java`

- [ ] Add `t_model_default_policy`.
- [ ] Implement Admin CRUD/upsert endpoint for default policies.
- [ ] Implement resolve priority: APP -> PROJECT -> GLOBAL -> PROVIDER_FALLBACK.
- [ ] Verify disabled policies and disabled model configs are ignored.

### Task 3: LLM Call Statistics

**Files:**
- Create: `hify-model/src/main/java/com/hify/model/api/LlmCallContext.java`
- Create: `hify-model/src/main/java/com/hify/model/api/LlmUsageQuery.java`
- Create: `hify-model/src/main/java/com/hify/model/api/LlmUsageStatsResp.java`
- Create: `hify-model/src/main/java/com/hify/model/api/LlmUsageStatsService.java`
- Create: `hify-model/src/main/java/com/hify/model/domain/LlmCallStatPo.java`
- Create: `hify-model/src/main/java/com/hify/model/domain/LlmUsageStatsServiceImpl.java`
- Create: `hify-model/src/main/java/com/hify/model/infra/LlmCallStatMapper.java`
- Create: `hify-model/src/main/java/com/hify/model/web/LlmUsageStatsController.java`
- Modify: `hify-model/src/main/java/com/hify/model/api/ChatRequest.java`
- Modify: `hify-model/src/main/java/com/hify/model/domain/LlmCallServiceImpl.java`
- Test: `hify-model/src/test/java/com/hify/model/domain/LlmCallServiceImplTest.java`

- [ ] Add `t_llm_call_stat`.
- [ ] Add optional `LlmCallContext` to `ChatRequest`.
- [ ] Record success/failure/fallback usage for chat and stream calls.
- [ ] Add grouped stats query by provider/model/user/app/project.

### Task 4: Fallback Priority

**Files:**
- Modify: `hify-model/src/main/java/com/hify/model/domain/LlmCallServiceImpl.java`
- Test: `hify-model/src/test/java/com/hify/model/domain/LlmCallServiceImplTest.java`

- [ ] Resolve fallback model from default policy before legacy provider-type fallback.
- [ ] Preserve old fallback behavior when no policy exists.
- [ ] Avoid fallback loops by excluding the primary provider/model.

### Task 5: OpenAI-Compatible Hardening

**Files:**
- Modify: `hify-model/src/main/java/com/hify/model/domain/adapter/ProviderAdapterFactory.java`
- Modify: `hify-model/src/main/java/com/hify/model/domain/adapter/OpenAiAdapter.java`
- Test: `hify-model/src/test/java/com/hify/model/domain/adapter/OpenAiCompatibleAdapterTest.java`

- [ ] Route `ALIBABA`, `DEEPSEEK`, `OPENAI_COMPATIBLE`, `MODEL_GATEWAY` through OpenAI-compatible adapter.
- [ ] Keep base URL normalization in the adapter and avoid duplicated `/v1`.
- [ ] Keep model listing failures non-fatal for manually configured embedding/chat models.

### Task 6: Verification

**Files:**
- Build/test commands only.

- [ ] Run `mvn -pl hify-model test`.
- [ ] Run `mvn -pl hify-app -am -DskipTests compile`.
- [ ] Run `npm run build --prefix hify-web` only if web files change.
