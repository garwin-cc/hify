-- V39: app bootstrap settings, rate limit quotas, and job run logs.

CREATE TABLE IF NOT EXISTS t_system_setting (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    setting_key   VARCHAR(128) NOT NULL,
    setting_value MEDIUMTEXT   NOT NULL,
    description   VARCHAR(500) NOT NULL DEFAULT '',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NOT NULL DEFAULT 0,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_setting_key_deleted (setting_key, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统配置';

CREATE TABLE IF NOT EXISTS t_rate_limit_quota (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    scope_type   VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL' COMMENT 'GLOBAL / PROJECT / APP',
    scope_id     BIGINT       NOT NULL DEFAULT 0,
    dimension    VARCHAR(32)  NOT NULL COMMENT 'USER / APP / API_KEY / PROVIDER / AGENT',
    quota_key    VARCHAR(128) NOT NULL DEFAULT '*',
    limit_count  INT          NOT NULL,
    window_sec   INT          NOT NULL DEFAULT 60,
    fail_open    TINYINT(1)   NOT NULL DEFAULT 1,
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    description  VARCHAR(500) NOT NULL DEFAULT '',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT       NOT NULL DEFAULT 0,
    deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quota_scope_dimension (scope_type, scope_id, dimension, quota_key, deleted),
    INDEX idx_quota_scope_enabled (scope_type, scope_id, enabled, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '限流配额策略';

CREATE TABLE IF NOT EXISTS t_app_job_run_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    job_name      VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL COMMENT 'SUCCESS / FAILED / SKIPPED',
    started_at    DATETIME(3)  NOT NULL,
    finished_at   DATETIME(3)  NULL,
    elapsed_ms    BIGINT       NOT NULL DEFAULT 0,
    affected_rows INT          NOT NULL DEFAULT 0,
    error_summary VARCHAR(1000) NOT NULL DEFAULT '',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NOT NULL DEFAULT 0,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_job_status_created (job_name, status, deleted, created_at),
    INDEX idx_job_created (deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '应用后台任务运行日志';

INSERT INTO t_rate_limit_quota (scope_type, scope_id, dimension, quota_key, limit_count, window_sec, fail_open, enabled, description)
SELECT 'GLOBAL', 0, 'USER', '*', 60, 60, 1, 1, '默认用户调用频率'
WHERE NOT EXISTS (
    SELECT 1 FROM t_rate_limit_quota
    WHERE scope_type = 'GLOBAL' AND scope_id = 0 AND dimension = 'USER' AND quota_key = '*' AND deleted = 0
);

INSERT INTO t_rate_limit_quota (scope_type, scope_id, dimension, quota_key, limit_count, window_sec, fail_open, enabled, description)
SELECT 'GLOBAL', 0, 'APP', '*', 300, 60, 1, 1, '默认应用调用频率'
WHERE NOT EXISTS (
    SELECT 1 FROM t_rate_limit_quota
    WHERE scope_type = 'GLOBAL' AND scope_id = 0 AND dimension = 'APP' AND quota_key = '*' AND deleted = 0
);

INSERT INTO t_rate_limit_quota (scope_type, scope_id, dimension, quota_key, limit_count, window_sec, fail_open, enabled, description)
SELECT 'GLOBAL', 0, 'API_KEY', '*', 120, 60, 1, 1, '默认 API Key 调用频率'
WHERE NOT EXISTS (
    SELECT 1 FROM t_rate_limit_quota
    WHERE scope_type = 'GLOBAL' AND scope_id = 0 AND dimension = 'API_KEY' AND quota_key = '*' AND deleted = 0
);

INSERT INTO t_rate_limit_quota (scope_type, scope_id, dimension, quota_key, limit_count, window_sec, fail_open, enabled, description)
SELECT 'GLOBAL', 0, 'PROVIDER', '*', 600, 60, 1, 1, '默认 Provider 调用频率'
WHERE NOT EXISTS (
    SELECT 1 FROM t_rate_limit_quota
    WHERE scope_type = 'GLOBAL' AND scope_id = 0 AND dimension = 'PROVIDER' AND quota_key = '*' AND deleted = 0
);

INSERT INTO t_rate_limit_quota (scope_type, scope_id, dimension, quota_key, limit_count, window_sec, fail_open, enabled, description)
SELECT 'GLOBAL', 0, 'AGENT', '*', 120, 60, 1, 1, '默认 Agent 调用频率'
WHERE NOT EXISTS (
    SELECT 1 FROM t_rate_limit_quota
    WHERE scope_type = 'GLOBAL' AND scope_id = 0 AND dimension = 'AGENT' AND quota_key = '*' AND deleted = 0
);
