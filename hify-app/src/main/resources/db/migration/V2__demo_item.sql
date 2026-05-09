CREATE TABLE IF NOT EXISTS t_demo_item (
    id         BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    name       VARCHAR(100) NOT NULL                              COMMENT '名称',
    status     TINYINT(1)   NOT NULL DEFAULT 1                    COMMENT '状态：1 启用 / 0 停用',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NOT NULL DEFAULT 0,
    deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_status_deleted (status, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'CRUD 演示表';
