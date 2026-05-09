-- V11: 工作流节点允许保留多份历史逻辑删除记录，唯一性由 Service 层校验当前定义

ALTER TABLE t_workflow_node
    DROP INDEX uk_workflow_node_key_deleted;

CREATE INDEX idx_workflow_node_key_deleted
    ON t_workflow_node (workflow_id, node_key, deleted);
