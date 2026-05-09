-- V17: 补齐工作流执行记录表的 created_by 字段，保持与 BaseEntity 映射一致。

ALTER TABLE t_workflow_run
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

ALTER TABLE t_workflow_node_run
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at;
