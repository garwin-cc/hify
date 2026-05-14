-- V34: Agent release governance, app publishing, tool governance, and debug trace fields.

ALTER TABLE t_agent
    ADD COLUMN draft_version_no INT NOT NULL DEFAULT 1 COMMENT '当前草稿版本号' AFTER enabled,
    ADD COLUMN published_version_id BIGINT NULL COMMENT '当前已发布版本 ID' AFTER draft_version_no,
    ADD COLUMN publish_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/TEST/PUBLISHED' AFTER published_version_id,
    ADD COLUMN max_tool_rounds INT NOT NULL DEFAULT 2 COMMENT '最大工具调用轮次' AFTER publish_status;

ALTER TABLE t_mcp_tool
    ADD COLUMN dangerous TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否危险工具' AFTER input_schema,
    ADD COLUMN permission_level VARCHAR(20) NOT NULL DEFAULT 'RUN' COMMENT 'RUN/MANAGE' AFTER dangerous,
    ADD COLUMN schema_validation_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用参数 schema 校验' AFTER permission_level;

ALTER TABLE t_conversation_trace
    ADD COLUMN agent_version_id BIGINT NULL COMMENT 'Agent 运行版本 ID' AFTER agent_name,
    ADD COLUMN agent_version_no INT NULL COMMENT 'Agent 运行版本号' AFTER agent_version_id,
    ADD COLUMN agent_system_prompt MEDIUMTEXT NULL COMMENT 'Agent system prompt 快照' AFTER agent_version_no,
    ADD COLUMN max_tool_rounds INT NULL COMMENT '运行时最大工具轮次' AFTER agent_system_prompt;

ALTER TABLE t_conversation_llm_trace
    ADD COLUMN request_summary JSON NULL COMMENT 'LLM 请求摘要，脱敏后保存' AFTER streaming;

CREATE TABLE IF NOT EXISTS t_agent_version (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id                BIGINT       NOT NULL COMMENT 'Agent ID',
    version_no              INT          NOT NULL COMMENT 'Agent 内版本号',
    status                  VARCHAR(20)  NOT NULL COMMENT 'DRAFT/TEST/PUBLISHED/ROLLED_BACK',
    name                    VARCHAR(100) NOT NULL,
    description             VARCHAR(500) NOT NULL DEFAULT '',
    system_prompt           MEDIUMTEXT   NOT NULL,
    model_config_id         BIGINT       NOT NULL,
    workflow_id             BIGINT       NULL,
    knowledge_base_ids_json JSON         NOT NULL,
    tool_ids_json           JSON         NOT NULL,
    temperature             DECIMAL(4,2) NULL,
    max_tokens              INT          NULL,
    max_context_turns       INT          NULL,
    memory_enabled          TINYINT(1)   NOT NULL DEFAULT 0,
    summary_trigger_message_count INT    NOT NULL DEFAULT 20,
    summary_max_tokens      INT          NOT NULL DEFAULT 800,
    summary_model_config_id BIGINT       NULL,
    max_tool_rounds         INT          NOT NULL DEFAULT 2,
    snapshot_json           JSON         NOT NULL,
    published_at            DATETIME(3)  NULL,
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by              BIGINT       NOT NULL DEFAULT 0,
    deleted                 TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_version (agent_id, version_no, deleted),
    INDEX idx_agent_status_created (agent_id, deleted, status, created_at),
    INDEX idx_status_created (status, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 版本快照';

CREATE TABLE IF NOT EXISTS t_agent_app (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id             BIGINT       NOT NULL,
    published_version_id BIGINT       NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    description          VARCHAR(500) NOT NULL DEFAULT '',
    web_enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    api_enabled          TINYINT(1)   NOT NULL DEFAULT 0,
    endpoint_path        VARCHAR(128) NOT NULL DEFAULT '',
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by           BIGINT       NOT NULL DEFAULT 0,
    deleted              TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_app_endpoint (endpoint_path, deleted),
    INDEX idx_agent_status_created (agent_id, deleted, status, created_at),
    INDEX idx_version_status (published_version_id, deleted, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 内部应用发布入口';

CREATE TABLE IF NOT EXISTS t_agent_api_key (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    agent_app_id    BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    key_prefix      VARCHAR(24)  NOT NULL,
    key_hash        VARCHAR(128) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_used_at    DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_key_hash_deleted (key_hash, deleted),
    INDEX idx_app_status_created (agent_app_id, deleted, status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 应用 API Key';
