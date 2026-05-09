ALTER TABLE t_model_config
    ADD COLUMN model_type VARCHAR(20) NOT NULL DEFAULT 'CHAT' COMMENT '模型用途：CHAT / EMBEDDING'
        AFTER model_id;

UPDATE t_model_config
SET model_type = 'EMBEDDING'
WHERE LOWER(model_id) LIKE '%embed%'
   OR LOWER(model_id) LIKE '%embedding%'
   OR LOWER(model_id) LIKE '%bge%'
   OR LOWER(model_id) LIKE '%nomic%';

CREATE INDEX idx_provider_type_deleted ON t_model_config (provider_id, model_type, deleted);
