-- V13: 工作流执行记录

CREATE TABLE IF NOT EXISTS t_workflow_run (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_id BIGINT       NOT NULL COMMENT '关联 t_workflow.id',
    status      VARCHAR(20)  NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    input       MEDIUMTEXT   NULL COMMENT '执行输入',
    output      MEDIUMTEXT   NULL COMMENT '执行输出',
    error       VARCHAR(500) NULL COMMENT '错误信息',
    elapsed_ms  INT          NULL COMMENT '执行耗时，毫秒',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3)  NULL COMMENT '结束时间',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_created (workflow_id, deleted, created_at),
    INDEX idx_status_created (status, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流执行记录';

CREATE TABLE IF NOT EXISTS t_workflow_node_run (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_run_id BIGINT       NOT NULL COMMENT '关联 t_workflow_run.id',
    node_key        VARCHAR(64)  NOT NULL COMMENT '节点 key',
    node_type       VARCHAR(30)  NOT NULL COMMENT '节点类型',
    status          VARCHAR(20)  NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    outputs         JSON         NULL COMMENT '节点执行后上下文快照',
    error           VARCHAR(500) NULL COMMENT '错误信息',
    elapsed_ms      INT          NULL COMMENT '执行耗时，毫秒',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    finished_at     DATETIME(3)  NULL COMMENT '结束时间',
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_node_run_run_id (workflow_run_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流节点执行记录';
