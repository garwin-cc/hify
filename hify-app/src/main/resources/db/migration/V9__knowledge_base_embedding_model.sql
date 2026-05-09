-- V9: 知识库补充 Embedding 模型配置 ID

ALTER TABLE t_knowledge_base
    ADD COLUMN embedding_model_config_id BIGINT NULL COMMENT 'Embedding 模型配置 ID'
        AFTER description;
