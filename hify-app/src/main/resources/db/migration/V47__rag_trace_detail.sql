ALTER TABLE t_rag_retrieval_trace
    ADD COLUMN detail_json JSON NULL COMMENT 'RAG 召回解释明细' AFTER error_message;
