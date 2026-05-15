ALTER TABLE t_knowledge_base
    ADD COLUMN hybrid_alpha DECIMAL(5,4) NOT NULL DEFAULT 0.7000 COMMENT '混合检索向量权重' AFTER retrieval_mode,
    ADD COLUMN metadata_filter_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用默认元数据过滤' AFTER rerank_top_n,
    ADD COLUMN default_metadata_filter_json JSON NULL COMMENT '默认元数据过滤规则' AFTER metadata_filter_enabled,
    ADD COLUMN active_index_version BIGINT NOT NULL DEFAULT 1 COMMENT '当前生效索引版本' AFTER default_metadata_filter_json,
    ADD COLUMN building_index_version BIGINT NULL COMMENT '构建中的索引版本' AFTER active_index_version,
    ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT 'READY/REBUILDING/FAILED' AFTER building_index_version;
