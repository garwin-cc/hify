-- V33: hify-model governance backend loop.

ALTER TABLE t_provider_health
    ADD COLUMN last_error_at DATETIME(3) NULL COMMENT '最近一次失败时间' AFTER last_success_at,
    ADD COLUMN last_alert_at DATETIME(3) NULL COMMENT '最近一次告警打开时间' AFTER last_error_at,
    ADD COLUMN alert_status VARCHAR(20) NOT NULL DEFAULT 'OK' COMMENT 'OK / OPEN' AFTER last_alert_at,
    ADD COLUMN success_count INT NOT NULL DEFAULT 0 COMMENT '累计成功探测次数' AFTER fail_count,
    ADD COLUMN total_check_count INT NOT NULL DEFAULT 0 COMMENT '累计探测次数' AFTER success_count;

CREATE TABLE IF NOT EXISTS t_model_default_policy (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    scope_type      VARCHAR(32)  NOT NULL COMMENT 'GLOBAL / PROJECT / APP / PROVIDER_FALLBACK',
    scope_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'GLOBAL 和 PROVIDER_FALLBACK 使用 0',
    model_type      VARCHAR(20)  NOT NULL DEFAULT 'CHAT' COMMENT 'CHAT / EMBEDDING',
    provider_type   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'PROVIDER_FALLBACK 策略使用',
    model_config_id BIGINT       NOT NULL COMMENT '默认模型配置 ID',
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_default_policy_scope (scope_type, scope_id, model_type, provider_type, deleted),
    INDEX idx_default_policy_model (model_config_id, deleted, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '模型默认策略';

CREATE TABLE IF NOT EXISTS t_llm_call_stat (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    trace_id         VARCHAR(64)  NOT NULL DEFAULT '',
    user_id          BIGINT       NULL,
    project_id       BIGINT       NULL,
    app_id           BIGINT       NULL,
    agent_id         BIGINT       NULL,
    provider_id      BIGINT       NOT NULL,
    provider_type    VARCHAR(64)  NOT NULL DEFAULT '',
    model_config_id  BIGINT       NOT NULL,
    model_id         VARCHAR(128) NOT NULL DEFAULT '',
    call_type        VARCHAR(20)  NOT NULL DEFAULT 'CHAT' COMMENT 'CHAT / STREAM / EMBEDDING',
    success          TINYINT(1)   NOT NULL DEFAULT 1,
    fallback_used    TINYINT(1)   NOT NULL DEFAULT 0,
    input_tokens     INT          NOT NULL DEFAULT 0,
    output_tokens    INT          NOT NULL DEFAULT 0,
    latency_ms       INT          NOT NULL DEFAULT 0,
    error_code       VARCHAR(64)  NOT NULL DEFAULT '',
    error_message    VARCHAR(500) NOT NULL DEFAULT '',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_llm_stat_created (deleted, created_at),
    INDEX idx_llm_stat_provider_created (provider_id, deleted, created_at),
    INDEX idx_llm_stat_model_created (model_config_id, deleted, created_at),
    INDEX idx_llm_stat_context_created (project_id, app_id, user_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'LLM 调用统计明细';
