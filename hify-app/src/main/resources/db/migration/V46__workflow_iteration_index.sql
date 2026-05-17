-- V46: Add workflow node run iteration index for ITERATION subflow troubleshooting.

ALTER TABLE t_workflow_node_run
    ADD COLUMN iteration_index INT NULL COMMENT 'ITERATION 子流程下标，从 0 开始' AFTER failure_strategy,
    ADD INDEX idx_node_run_iteration (workflow_run_id, node_key, iteration_index, deleted);
