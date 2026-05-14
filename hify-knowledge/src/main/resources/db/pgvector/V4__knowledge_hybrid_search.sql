ALTER TABLE t_knowledge_chunk
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

UPDATE t_knowledge_chunk
   SET search_vector = to_tsvector('simple', coalesce(content, ''))
 WHERE search_vector IS NULL;

CREATE INDEX IF NOT EXISTS idx_kb_dim_created
    ON t_knowledge_chunk (knowledge_base_id, deleted, created_at);

CREATE INDEX IF NOT EXISTS idx_metadata_gin
    ON t_knowledge_chunk
    USING gin (metadata);

CREATE INDEX IF NOT EXISTS idx_search_vector_gin
    ON t_knowledge_chunk
    USING gin (search_vector);

-- Optional for pgvector versions that support HNSW. Keep disabled by default for compatibility.
-- CREATE INDEX IF NOT EXISTS idx_embedding_hnsw
--     ON t_knowledge_chunk
--     USING hnsw (embedding vector_cosine_ops);
