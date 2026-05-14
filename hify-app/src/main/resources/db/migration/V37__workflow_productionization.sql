-- V37: Workflow productionization publish, version governance, triggers, review todo, and runtime policy fields.

ALTER TABLE t_workflow_version
    ADD COLUMN version_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/GRAY/ARCHIVED' AFTER change_summary,
    ADD COLUMN parent_version_id BIGINT NULL COMMENT '来源版本 ID，用于回滚链路' AFTER version_status,
    ADD COLUMN gray_percent INT NOT NULL DEFAULT 0 COMMENT '灰度百分比 0-100' AFTER parent_version_id,
    ADD COLUMN checksum VARCHAR(64) NOT NULL DEFAULT '' COMMENT '快照 checksum' AFTER gray_percent,
    ADD COLUMN published_by BIGINT NULL COMMENT '发布人用户 ID' AFTER checksum,
    ADD COLUMN published_at DATETIME(3) NULL COMMENT '发布时间' AFTER published_by;

ALTER TABLE t_workflow_run
    ADD COLUMN trigger_type VARCHAR(20) NULL COMMENT 'MANUAL/WEBHOOK/SCHEDULE/API/TOOL' AFTER run_mode,
    ADD COLUMN trigger_id BIGINT NULL COMMENT '触发器 ID' AFTER trigger_type,
    ADD COLUMN publish_id BIGINT NULL COMMENT '发布记录 ID' AFTER trigger_id,
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/WEB_APP/API_ENDPOINT/TOOL/WEBHOOK/SCHEDULE' AFTER publish_id,
    ADD INDEX idx_source_status_created (source, status, deleted, created_at),
    ADD INDEX idx_publish_created (publish_id, deleted, created_at);

ALTER TABLE t_workflow_node_run
    ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 COMMENT '当前节点执行尝试次数' AFTER status,
    ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 COMMENT '节点最大尝试次数' AFTER attempt_no,
    ADD COLUMN timeout_seconds INT NULL COMMENT '节点 timeout 秒数' AFTER max_attempts,
    ADD COLUMN failure_strategy VARCHAR(30) NOT NULL DEFAULT 'FAIL_RUN' COMMENT 'FAIL_RUN/CONTINUE/ERROR_BRANCH/HUMAN_REVIEW' AFTER timeout_seconds;

ALTER TABLE t_workflow_review_task
    ADD COLUMN assignee_user_id BIGINT NULL COMMENT '审批人用户 ID' AFTER output_variable,
    ADD COLUMN assignee_username VARCHAR(100) NULL COMMENT '审批人用户名' AFTER assignee_user_id,
    ADD COLUMN due_at DATETIME(3) NULL COMMENT '审批截止时间' AFTER assignee_username,
    ADD COLUMN timeout_action VARCHAR(30) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/APPROVE/REJECT/CANCEL' AFTER due_at,
    ADD COLUMN notified_at DATETIME(3) NULL COMMENT '通知时间' AFTER timeout_action,
    ADD COLUMN expired_at DATETIME(3) NULL COMMENT '超时时间' AFTER notified_at,
    ADD INDEX idx_review_assignee_status (assignee_user_id, status, deleted, due_at),
    ADD INDEX idx_review_status_due (status, deleted, due_at);

CREATE TABLE IF NOT EXISTS t_workflow_publish (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_id         BIGINT       NOT NULL COMMENT '关联 t_workflow.id',
    workflow_version_id BIGINT       NOT NULL COMMENT '发布绑定版本 ID',
    publish_type        VARCHAR(30)  NOT NULL COMMENT 'WEB_APP/API_ENDPOINT/TOOL',
    publish_status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/ARCHIVED',
    endpoint_key        VARCHAR(100) NULL COMMENT 'API/Webhook 调用 key',
    tool_key            VARCHAR(100) NULL COMMENT '受控工具 key',
    display_name        VARCHAR(100) NOT NULL DEFAULT '' COMMENT '发布展示名',
    gray_percent        INT          NOT NULL DEFAULT 0 COMMENT '灰度百分比',
    published_by        BIGINT       NULL COMMENT '发布人',
    published_at        DATETIME(3)  NULL COMMENT '发布时间',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_type_status (workflow_id, publish_type, publish_status, deleted),
    UNIQUE KEY uk_endpoint_key_deleted (endpoint_key, deleted),
    UNIQUE KEY uk_tool_key_deleted (tool_key, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流发布记录';

CREATE TABLE IF NOT EXISTS t_workflow_trigger (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_id         BIGINT       NOT NULL COMMENT '关联 t_workflow.id',
    workflow_version_id BIGINT       NULL COMMENT '触发绑定版本 ID',
    trigger_type        VARCHAR(20)  NOT NULL COMMENT 'WEBHOOK/SCHEDULE',
    trigger_key         VARCHAR(100) NULL COMMENT 'Webhook key',
    cron_expression     VARCHAR(100) NULL COMMENT '定时表达式',
    enabled             TINYINT(1)   NOT NULL DEFAULT 1,
    next_fire_at        DATETIME(3)  NULL COMMENT '下次触发时间',
    last_fire_at        DATETIME(3)  NULL COMMENT '上次触发时间',
    last_run_id         BIGINT       NULL COMMENT '上次触发运行 ID',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_type_enabled (workflow_id, trigger_type, enabled, deleted),
    UNIQUE KEY uk_trigger_key_deleted (trigger_key, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流触发器';

CREATE TABLE IF NOT EXISTS t_workflow_version_diff (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    workflow_id      BIGINT      NOT NULL COMMENT '关联 t_workflow.id',
    left_version_id  BIGINT      NOT NULL COMMENT '左侧版本 ID',
    right_version_id BIGINT      NOT NULL COMMENT '右侧版本 ID',
    summary_json     JSON        NOT NULL COMMENT '结构化 diff 摘要',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_diff_deleted (left_version_id, right_version_id, deleted),
    INDEX idx_workflow_created (workflow_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流版本差异摘要';
