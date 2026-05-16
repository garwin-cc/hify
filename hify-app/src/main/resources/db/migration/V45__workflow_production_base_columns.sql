-- V45: 补齐工作流生产化表的 created_by 字段，保持与 BaseEntity 映射一致。

ALTER TABLE t_workflow_publish
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

ALTER TABLE t_workflow_trigger
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

ALTER TABLE t_workflow_version_diff
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at;
