DROP INDEX IF EXISTS idx_embedding_ivfflat;

ALTER TABLE t_knowledge_chunk
    ALTER COLUMN embedding TYPE vector;
