-- V48: Stage 7 operations capacity and local log archive.

CREATE TABLE IF NOT EXISTS t_ops_log_archive (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    archive_type  VARCHAR(32)  NOT NULL COMMENT 'RUNTIME_LOG / AUDIT_LOG / JOB_LOG',
    source_table  VARCHAR(100) NOT NULL,
    source_id     BIGINT       NOT NULL,
    payload_json  MEDIUMTEXT   NOT NULL,
    archived_at   DATETIME(3)  NOT NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NOT NULL DEFAULT 0,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_archive_source (archive_type, source_table, source_id, deleted),
    INDEX idx_archive_type_time (archive_type, deleted, archived_at),
    INDEX idx_archive_created (deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '本地日志归档表';
