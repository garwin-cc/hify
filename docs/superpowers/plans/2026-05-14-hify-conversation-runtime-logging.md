# Hify Conversation Runtime Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend runtime stability, cursor pagination, log querying, feedback, and rate limiting to `hify-conversation`.

**Architecture:** Reuse existing conversation trace tables and common rate limit service. Keep old list APIs compatible while adding cursor-based APIs for scalable paths.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, JUnit 5, Mockito, Redis-backed rate limiting.

---

### Task 1: Schema And DTOs

**Files:**
- Create: `hify-app/src/main/resources/db/migration/V35__conversation_runtime_logging.sql`
- Create DTOs under `hify-conversation/src/main/java/com/hify/conversation/api/`
- Create `MessageFeedbackPo` and mapper.

- [x] Add trace app/user/api key columns and message feedback table.
- [x] Add cursor query/response DTOs.
- [x] Add log query/response DTOs.
- [x] Add feedback request/response DTOs.

### Task 2: Service API

**Files:**
- Modify: `ConversationService.java`
- Modify: `ConversationController.java`
- Modify: `SendMessageReq.java`

- [x] Add cursor list methods.
- [x] Add log query method.
- [x] Add feedback upsert method.
- [x] Add optional user/app/apiKey fields to send request.

### Task 3: Runtime Implementation

**Files:**
- Modify: `ConversationServiceImpl.java`

- [x] Inject optional `llmStreamExecutor`.
- [x] Use stream executor for SSE tasks.
- [x] Add pre-submit capacity guard.
- [x] Add common rate limit checks.
- [x] Persist user/app/apiKey into session and trace.

### Task 4: Query And Feedback Implementation

**Files:**
- Modify: `ConversationServiceImpl.java`
- Create tests in `ConversationServiceImplTest.java` or focused test classes.

- [x] Implement cursor session query.
- [x] Implement cursor message query.
- [x] Implement log center query.
- [x] Implement feedback upsert.

### Task 5: Verification

- [x] Run `mvn -pl hify-conversation -am test`.
- [x] Run `mvn -pl hify-app -am -DskipTests compile`.
- [x] Check `git status --short`.
