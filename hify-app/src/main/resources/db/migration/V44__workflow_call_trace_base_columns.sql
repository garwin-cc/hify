-- V44: 补齐工作流节点调用追踪表的 created_by 字段，保持与 BaseEntity 映射一致。

ALTER TABLE t_workflow_node_call_trace
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at;
