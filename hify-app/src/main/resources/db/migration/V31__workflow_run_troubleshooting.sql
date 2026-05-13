-- V31: Workflow run troubleshooting fields and external call traces.

ALTER TABLE t_workflow_run
    ADD COLUMN trace_id VARCHAR(64) NULL COMMENT '统一 traceId' AFTER workflow_version_id,
    ADD COLUMN rerun_from_run_id BIGINT NULL COMMENT '重跑来源 workflow run id' AFTER trace_id,
    ADD INDEX idx_workflow_trace (trace_id, deleted);

ALTER TABLE t_workflow_node_run
    ADD COLUMN input_snapshot JSON NULL COMMENT '节点执行前上下文快照' AFTER status,
    ADD COLUMN started_at DATETIME(3) NULL COMMENT '节点开始时间' AFTER input_snapshot;

CREATE TABLE IF NOT EXISTS t_workflow_node_call_trace (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_run_id      BIGINT       NOT NULL COMMENT '关联 t_workflow_run.id',
    workflow_node_run_id BIGINT       NULL COMMENT '关联 t_workflow_node_run.id',
    node_key             VARCHAR(100) NOT NULL COMMENT '节点 key',
    node_type            VARCHAR(30)  NOT NULL COMMENT '节点类型',
    call_type            VARCHAR(30)  NOT NULL COMMENT 'LLM/API_CALL/MCP/HUMAN_REVIEW/CODE_TASK',
    target               VARCHAR(255) NOT NULL DEFAULT '' COMMENT '调用目标摘要',
    request_snapshot     JSON         NULL COMMENT '脱敏后的请求快照',
    response_snapshot    JSON         NULL COMMENT '脱敏后的响应快照',
    status               VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/FAILED/WAITING',
    error_message        VARCHAR(500) NULL COMMENT '错误信息',
    duration_ms          INT          NULL COMMENT '调用耗时，毫秒',
    started_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at          DATETIME(3)  NULL COMMENT '结束时间',
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted              TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_run_node_created (workflow_run_id, node_key, deleted, created_at),
    INDEX idx_node_run_created (workflow_node_run_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流节点外部调用追踪';
