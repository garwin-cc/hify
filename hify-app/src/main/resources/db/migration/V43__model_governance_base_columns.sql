-- V43: 补齐模型治理表的 created_by 字段，保持与 BaseEntity 映射一致。

ALTER TABLE t_model_default_policy
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

ALTER TABLE t_llm_call_stat
    ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at;
