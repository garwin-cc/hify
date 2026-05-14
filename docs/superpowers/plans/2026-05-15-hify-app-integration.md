# hify-app Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `hify-app` the production bootstrap and integration layer for migrations, initialization, health checks, maintenance jobs, and app-level integration tests.

**Architecture:** Keep business ownership in existing modules and add only app-level orchestration. New tables store system settings, rate limit quotas, and job run logs; new app services initialize defaults, expose liveness/readiness/deep health, and record maintenance jobs without duplicating business tables.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, MySQL/H2, Redis, pgvector JdbcTemplate, JUnit 5, MockMvc.

---

### Task 1: Add App Bootstrap Migration

**Files:**
- Create: `hify-app/src/main/resources/db/migration/V39__app_bootstrap_quota_job_log.sql`

- [ ] **Step 1: Write migration**

Create `V39__app_bootstrap_quota_job_log.sql` with:

```sql
CREATE TABLE IF NOT EXISTS t_system_setting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(128) NOT NULL,
    setting_value MEDIUMTEXT NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_setting_key_deleted (setting_key, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统配置';
```

Also create `t_rate_limit_quota` and `t_app_job_run_log`, and insert default quota rows for USER, APP, API_KEY, PROVIDER, AGENT.

- [ ] **Step 2: Verify SQL style**

Run:

```bash
rg -n "TODO|TBD|DOUBLE|FLOAT|VARCHAR\\)" hify-app/src/main/resources/db/migration/V39__app_bootstrap_quota_job_log.sql
```

Expected: no output.

### Task 2: Add App Bootstrap Domain Objects

**Files:**
- Create: `hify-app/src/main/java/com/hify/app/domain/SystemSettingPo.java`
- Create: `hify-app/src/main/java/com/hify/app/domain/RateLimitQuotaPo.java`
- Create: `hify-app/src/main/java/com/hify/app/domain/AppJobRunLogPo.java`
- Create: `hify-app/src/main/java/com/hify/app/infra/SystemSettingMapper.java`
- Create: `hify-app/src/main/java/com/hify/app/infra/RateLimitQuotaMapper.java`
- Create: `hify-app/src/main/java/com/hify/app/infra/AppJobRunLogMapper.java`

- [ ] **Step 1: Add PO classes**

Use `BaseEntity`, `@TableName`, Lombok `@Data`, and fields matching the migration.

- [ ] **Step 2: Add Mapper interfaces**

Each mapper extends `BaseMapper<...>` and lives under `com.hify.app.infra` so existing `@MapperScan("com.hify.**.infra")` picks it up.

### Task 3: Implement System Initialization

**Files:**
- Create: `hify-app/src/main/java/com/hify/app/domain/SystemInitializationProperties.java`
- Create: `hify-app/src/main/java/com/hify/app/domain/SystemInitializationRunner.java`
- Test: `hify-app/src/test/java/com/hify/app/SystemInitializationRunnerTest.java`

- [ ] **Step 1: Write failing test**

Test default quota/model initialization against H2 mock schema: when provider init is enabled and no provider exists, runner inserts one provider, one chat model, optional embedding model, and global model policies.

- [ ] **Step 2: Run test and verify RED**

Run:

```bash
mvn -pl hify-app -Dtest=SystemInitializationRunnerTest test
```

Expected: fail because classes do not exist.

- [ ] **Step 3: Implement runner**

Runner must:

- Ensure default workspace/project rows if tables exist.
- Insert default provider/model only when `hify.init.default-provider.enabled=true`.
- Insert default rate limit quota rows if table exists.
- Never overwrite existing provider, model policy, or quota rows.

- [ ] **Step 4: Verify GREEN**

Run the same test and expect pass.

### Task 4: Split Health Checks

**Files:**
- Modify: `hify-app/src/main/java/com/hify/web/HealthController.java`
- Test: `hify-app/src/test/java/com/hify/app/HealthControllerIntegrationTest.java`

- [ ] **Step 1: Write failing tests**

Add tests for:

- `GET /api/v1/health/liveness` returns status `UP` and has no dependency components.
- `GET /api/v1/health/readiness` includes mysql, redis, pgvector.
- `GET /api/v1/health/deep` includes providerSummary.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
mvn -pl hify-app -Dtest=HealthControllerIntegrationTest test
```

Expected: fail because new routes do not exist.

- [ ] **Step 3: Implement endpoints**

Keep `/api/v1/health` as deep health compatibility endpoint. Use existing SQL ping methods and add provider health summary by reading `ProviderMapper` and `ProviderHealthMapper`.

- [ ] **Step 4: Verify GREEN**

Run the same test and expect pass.

### Task 5: Add Maintenance Job and Job Run Logging

**Files:**
- Create: `hify-app/src/main/java/com/hify/app/domain/AppJobRunLogger.java`
- Create: `hify-app/src/main/java/com/hify/app/domain/AppMaintenanceProperties.java`
- Create: `hify-app/src/main/java/com/hify/app/domain/AppMaintenanceJob.java`
- Test: `hify-app/src/test/java/com/hify/app/AppMaintenanceJobTest.java`

- [ ] **Step 1: Write failing tests**

Tests should verify:

- Expired user sessions are revoked.
- A successful job writes `t_app_job_run_log` with status `SUCCESS`.
- A failing job writes `FAILED` with error summary.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
mvn -pl hify-app -Dtest=AppMaintenanceJobTest test
```

Expected: fail because job/log classes do not exist.

- [ ] **Step 3: Implement job logger and maintenance job**

Use `JdbcTemplate` for low-coupling cleanup SQL. Guard each cleanup with table-existence checks so mock/incremental databases do not fail startup.

- [ ] **Step 4: Verify GREEN**

Run the same test and expect pass.

### Task 6: Extend Mock Integration Schema

**Files:**
- Modify: `hify-app/src/test/resources/db/mock/schema.sql`
- Modify: `hify-app/src/test/resources/application-mock.yml`

- [ ] **Step 1: Add missing mock tables and columns**

Add app bootstrap tables plus columns needed by latest migrations: workspace/project fields, trace/log fields, app/api key tables, workflow publishing tables if required by tests.

- [ ] **Step 2: Disable scheduled app jobs in mock profile**

Set:

```yaml
hify:
  jobs:
    enabled: false
```

### Task 7: Add App-Level Chain Integration Tests

**Files:**
- Create or extend: `hify-app/src/test/java/com/hify/app/AppCoreChainIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

Cover:

- Login with initialized admin when auth is enabled.
- Publish Agent to App and create API Key.
- Use existing chat/RAG/Workflow/MCP mocks to verify route wiring and persistence.

- [ ] **Step 2: Run integration tests**

Run:

```bash
mvn -pl hify-app -Dtest=AppCoreChainIntegrationTest test
```

Expected: pass after Tasks 1-6.

### Task 8: Final Verification

- [ ] **Step 1: Run app module tests**

```bash
mvn -pl hify-app -am test
```

Expected: build success.

- [ ] **Step 2: Run compile for all backend modules**

```bash
mvn -pl hify-app -am -DskipTests compile
```

Expected: build success.

### Self-Review

- Spec coverage: migration, initialization, health checks, jobs and integration tests are covered.
- Placeholder scan: no placeholders are intentionally left.
- Type consistency: PO/Mapper/Runner/Controller names match planned files.
