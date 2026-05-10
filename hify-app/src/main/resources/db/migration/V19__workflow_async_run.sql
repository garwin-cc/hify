-- V19: 工作流异步执行状态字段。

ALTER TABLE t_workflow_run
    ADD COLUMN current_node_key VARCHAR(100) NULL COMMENT '当前执行节点 key' AFTER error,
    ADD COLUMN timeout_at DATETIME(3) NULL COMMENT '执行超时时间点' AFTER current_node_key,
    ADD COLUMN run_mode VARCHAR(20) NOT NULL DEFAULT 'SYNC' COMMENT 'SYNC/ASYNC' AFTER timeout_at,
    ADD INDEX idx_run_mode_status (run_mode, status, deleted, created_at);
