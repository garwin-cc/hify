# Hify Knowledge RAG Production Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend production readiness for knowledge document processing, rebuild/revectorize, metadata filtering, hybrid retrieval, pgvector governance, permission filtering, and observability.

**Architecture:** Extend the existing modular monolith without adding new cross-module domain dependencies. Keep `KnowledgeService` as the cross-module API, persist knowledge processing tasks in MySQL, and keep vector/keyword retrieval inside `KnowledgeVectorRepository`.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, PostgreSQL/pgvector, JUnit 5, Mockito.

---

### Task 1: Schema And DTOs

**Files:**
- Create: `hify-app/src/main/resources/db/migration/V36__knowledge_rag_production.sql`
- Create: `hify-knowledge/src/main/resources/db/pgvector/V4__knowledge_hybrid_search.sql`
- Create: `hify-knowledge/src/main/java/com/hify/knowledge/api/KnowledgeRebuildReq.java`
- Create: `hify-knowledge/src/main/java/com/hify/knowledge/api/KnowledgeTaskResp.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/api/KnowledgeSearchReq.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/api/KnowledgeSearchResp.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/api/KnowledgeDocumentResp.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeBasePo.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeDocumentPo.java`

- [x] Add MySQL fields for knowledge visibility, document metadata, task progress, and task table.
- [x] Add pgvector fields and indexes for keyword and metadata search.
- [x] Add request/response DTOs for rebuild and task observability.
- [x] Extend search request/response with metadata filters and keyword score.

### Task 2: Task Persistence And Recovery

**Files:**
- Create: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeTaskPo.java`
- Create: `hify-knowledge/src/main/java/com/hify/knowledge/infra/KnowledgeTaskMapper.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeServiceImpl.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeDocumentRecoveryRunner.java`

- [x] Persist document processing tasks before queue submission.
- [x] Update task status on running, success, failure, cancellation, and queue rejection.
- [x] Requeue pending/running tasks during recovery.
- [x] Preserve document status compatibility for existing APIs.

### Task 3: Rebuild And Revectorize

**Files:**
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/api/KnowledgeService.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/web/KnowledgeController.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/web/DocumentController.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeServiceImpl.java`

- [x] Add knowledge base rebuild API.
- [x] Add document revectorize API.
- [x] Reset documents and clear old chunks before rebuild processing.
- [x] Reuse the same document processing pipeline for all rebuild paths.

### Task 4: Metadata, Hybrid Search, And Permission Filtering

**Files:**
- Create: `hify-knowledge/src/main/java/com/hify/knowledge/api/KnowledgeSearchFilter.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeVectorRepository.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/infra/PgvectorKnowledgeVectorRepository.java`
- Modify: `hify-knowledge/src/main/java/com/hify/knowledge/domain/KnowledgeServiceImpl.java`

- [x] Build filter object from `KnowledgeSearchReq`.
- [x] Add repository vector search with filter.
- [x] Add repository keyword search with filter.
- [x] Merge HYBRID results with weighted final score.
- [x] Apply project/visibility filtering before vector repository calls.

### Task 5: Tests And Verification

**Files:**
- Modify: `hify-knowledge/src/test/java/com/hify/knowledge/domain/KnowledgeServiceImplTest.java`

- [x] Add unit tests for task creation and queue submission.
- [x] Add unit tests for rebuild and revectorize.
- [x] Add unit tests for metadata filter propagation.
- [x] Add unit tests for hybrid score merge.
- [x] Run `mvn -pl hify-knowledge -am test`.
- [x] Run `mvn -pl hify-app -am -DskipTests compile`.
