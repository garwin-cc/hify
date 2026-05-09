-- -----------------------------------------------------------------------------
-- V4: t_agent 补充 knowledge_base_ids 字段
--     为 knowledge 模块预留绑定位置，本期不做实际 RAG 逻辑
-- -----------------------------------------------------------------------------

ALTER TABLE t_agent
    ADD COLUMN knowledge_base_ids JSON NOT NULL DEFAULT (JSON_ARRAY())
        COMMENT '关联知识库 ID 列表，如 [1,2,3]，knowledge 模块实现后生效'
        AFTER model_config_id;
