-- V20: 工作流执行事件流，用于 SSE 断线恢复和执行过程审计。

CREATE TABLE IF NOT EXISTS t_workflow_run_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_run_id BIGINT       NOT NULL COMMENT '关联 t_workflow_run.id',
    event_seq       INT          NOT NULL COMMENT '同一次 run 内递增事件序号',
    event_type      VARCHAR(40)  NOT NULL COMMENT 'RUN_STARTED/NODE_STARTED/NODE_SUCCEEDED/NODE_FAILED/RUN_SUCCEEDED/RUN_FAILED/RUN_TIMEOUT/HEARTBEAT',
    node_key        VARCHAR(100) NULL COMMENT '关联节点 key，run 级事件为空',
    status          VARCHAR(20)  NULL COMMENT '事件对应状态',
    payload         JSON         NULL COMMENT '事件负载',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_seq (workflow_run_id, event_seq),
    INDEX idx_run_created (workflow_run_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流执行事件流';
