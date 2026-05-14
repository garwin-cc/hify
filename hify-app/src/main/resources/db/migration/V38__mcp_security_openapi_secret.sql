-- V38: MCP security, Secret references, OpenAPI tools, and searchable audit.

ALTER TABLE t_mcp_server
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT/WORKSPACE/PUBLIC' AFTER enabled,
    ADD COLUMN share_scope VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE/SHARED' AFTER visibility,
    ADD COLUMN secret_id BIGINT NULL COMMENT '凭证引用 ID' AFTER share_scope,
    ADD COLUMN connect_timeout_ms INT NOT NULL DEFAULT 3000 COMMENT '连接超时毫秒' AFTER secret_id,
    ADD COLUMN read_timeout_ms INT NOT NULL DEFAULT 30000 COMMENT '读取超时毫秒' AFTER connect_timeout_ms,
    ADD COLUMN retry_times INT NOT NULL DEFAULT 0 COMMENT '工具调用重试次数' AFTER read_timeout_ms,
    ADD COLUMN retry_interval_ms INT NOT NULL DEFAULT 300 COMMENT '重试间隔毫秒' AFTER retry_times,
    ADD COLUMN fallback_strategy VARCHAR(30) NOT NULL DEFAULT 'FAIL_FAST' COMMENT 'FAIL_FAST/RETURN_ERROR_MESSAGE' AFTER retry_interval_ms,
    ADD INDEX idx_project_enabled_created (project_id, enabled, deleted, created_at),
    ADD INDEX idx_workspace_visibility (workspace_id, visibility, deleted, created_at);

ALTER TABLE t_mcp_tool
    ADD COLUMN tool_type VARCHAR(20) NOT NULL DEFAULT 'MCP' COMMENT 'MCP/OPENAPI' AFTER mcp_server_id,
    ADD COLUMN openapi_tool_id BIGINT NULL COMMENT '关联 OpenAPI 工具 ID' AFTER tool_type,
    ADD COLUMN timeout_ms INT NULL COMMENT '工具级超时毫秒' AFTER schema_validation_enabled,
    ADD COLUMN retry_times INT NULL COMMENT '工具级重试次数' AFTER timeout_ms,
    ADD COLUMN fallback_strategy VARCHAR(30) NULL COMMENT '工具级失败降级策略' AFTER retry_times,
    ADD INDEX idx_tool_type_created (tool_type, deleted, created_at);

ALTER TABLE t_mcp_tool_call_audit
    ADD COLUMN workspace_id BIGINT NULL COMMENT '空间 ID' AFTER trace_id,
    ADD COLUMN project_id BIGINT NULL COMMENT '项目 ID' AFTER workspace_id,
    ADD COLUMN agent_id BIGINT NULL COMMENT 'Agent ID' AFTER project_id,
    ADD COLUMN app_id BIGINT NULL COMMENT '应用 ID' AFTER agent_id,
    ADD COLUMN api_key_id BIGINT NULL COMMENT 'API Key ID' AFTER app_id,
    ADD COLUMN user_id BIGINT NULL COMMENT '用户 ID' AFTER api_key_id,
    ADD COLUMN workflow_id BIGINT NULL COMMENT 'Workflow ID' AFTER workflow_run_id,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'FAILED' COMMENT 'SUCCESS/FAILED/TIMEOUT/SCHEMA_INVALID' AFTER tool_name,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '实际重试次数' AFTER status,
    ADD COLUMN timeout_ms INT NULL COMMENT '调用超时毫秒' AFTER retry_count,
    ADD COLUMN source_id VARCHAR(100) NULL COMMENT '调用来源 ID' AFTER timeout_ms,
    ADD INDEX idx_project_tool_status (project_id, tool_name, status, deleted, created_at),
    ADD INDEX idx_agent_created (agent_id, deleted, created_at),
    ADD INDEX idx_user_created (user_id, deleted, created_at);

CREATE TABLE IF NOT EXISTS t_tool_secret (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    workspace_id    BIGINT       NOT NULL DEFAULT 1,
    project_id      BIGINT       NOT NULL DEFAULT 1,
    name            VARCHAR(100) NOT NULL,
    secret_type     VARCHAR(30)  NOT NULL COMMENT 'API_KEY/BEARER/BASIC/CUSTOM',
    key_prefix      VARCHAR(32)  NOT NULL DEFAULT '',
    encrypted_value MEDIUMTEXT   NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_project_status_created (project_id, status, deleted, created_at),
    UNIQUE KEY uk_project_name_deleted (project_id, name, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工具调用凭证';

CREATE TABLE IF NOT EXISTS t_openapi_tool_source (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    workspace_id    BIGINT       NOT NULL DEFAULT 1,
    project_id      BIGINT       NOT NULL DEFAULT 1,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NOT NULL DEFAULT '',
    base_url        VARCHAR(500) NOT NULL,
    spec_json       JSON         NOT NULL,
    secret_id       BIGINT       NULL,
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_project_enabled_created (project_id, enabled, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'OpenAPI 工具源';

CREATE TABLE IF NOT EXISTS t_openapi_tool (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    source_id        BIGINT       NOT NULL COMMENT 'OpenAPI source ID',
    name             VARCHAR(200) NOT NULL,
    description      VARCHAR(1000) NOT NULL DEFAULT '',
    http_method      VARCHAR(10)  NOT NULL,
    path             VARCHAR(500) NOT NULL,
    input_schema     JSON         NULL,
    response_schema  JSON         NULL,
    enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by       BIGINT       NOT NULL DEFAULT 0,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_source_enabled_created (source_id, enabled, deleted, created_at),
    INDEX idx_source_name_deleted (source_id, name, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'OpenAPI 解析出的工具';
