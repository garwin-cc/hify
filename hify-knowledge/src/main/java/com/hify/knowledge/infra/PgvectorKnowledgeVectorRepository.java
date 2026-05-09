package com.hify.knowledge.infra;

import com.hify.knowledge.domain.KnowledgeChunk;
import com.hify.knowledge.domain.KnowledgeSearchHit;
import com.hify.knowledge.domain.KnowledgeVectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Profile("!mock")
@RequiredArgsConstructor
public class PgvectorKnowledgeVectorRepository implements KnowledgeVectorRepository {

    private final @Qualifier("pgvectorNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Long upsert(KnowledgeChunk chunk, List<Double> embedding) {
        String sql = """
                INSERT INTO t_knowledge_chunk
                    (knowledge_base_id, document_id, chunk_index, content, metadata, embedding)
                VALUES
                    (:knowledgeBaseId, :documentId, :chunkIndex, :content, CAST(:metadata AS jsonb), CAST(:embedding AS vector))
                ON CONFLICT (knowledge_base_id, document_id, chunk_index) WHERE deleted = false
                DO UPDATE SET
                    content = EXCLUDED.content,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding,
                    updated_at = now()
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("knowledgeBaseId", chunk.getKnowledgeBaseId())
                .addValue("documentId", chunk.getDocumentId())
                .addValue("chunkIndex", chunk.getChunkIndex())
                .addValue("content", chunk.getContent())
                .addValue("metadata", chunk.getMetadataJson())
                .addValue("embedding", toVectorLiteral(embedding));
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    @Override
    public void saveDocumentChunks(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO t_knowledge_chunk
                    (knowledge_base_id, document_id, chunk_index, content, token_count, metadata, embedding)
                VALUES
                    (:knowledgeBaseId, :documentId, :chunkIndex, :content, :tokenCount,
                     CAST(:metadataJson AS jsonb), CAST(:embeddingLiteral AS vector))
                ON CONFLICT (knowledge_base_id, document_id, chunk_index) WHERE deleted = false
                DO UPDATE SET
                    content = EXCLUDED.content,
                    token_count = EXCLUDED.token_count,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding,
                    updated_at = now()
                """;
        MapSqlParameterSource[] params = chunks.stream()
                .map(chunk -> new MapSqlParameterSource()
                        .addValue("knowledgeBaseId", chunk.getKnowledgeBaseId())
                        .addValue("documentId", chunk.getDocumentId())
                        .addValue("chunkIndex", chunk.getChunkIndex())
                        .addValue("content", chunk.getContent())
                        .addValue("tokenCount", chunk.getTokenCount())
                        .addValue("metadataJson", chunk.getMetadataJson())
                        .addValue("embeddingLiteral", toVectorLiteral(chunk.getEmbedding())))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(sql, params);
    }

    @Override
    public List<KnowledgeSearchHit> search(List<Long> knowledgeBaseIds, List<Double> queryEmbedding, int topK) {
        boolean filterByKnowledgeBase = knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty();
        String filterClause = filterByKnowledgeBase ? "AND knowledge_base_id IN (:knowledgeBaseIds)" : "";
        String sql = """
                SELECT id, knowledge_base_id, document_id, chunk_index, content, metadata::text AS metadata_json,
                       created_at, 1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS score
                  FROM t_knowledge_chunk
                 WHERE deleted = false
                   AND vector_dims(embedding) = :queryDimension
                %s
                 ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
                 LIMIT :topK
                """.formatted(filterClause);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryEmbedding", toVectorLiteral(queryEmbedding))
                .addValue("queryDimension", queryEmbedding.size())
                .addValue("topK", topK);
        if (filterByKnowledgeBase) {
            params.addValue("knowledgeBaseIds", knowledgeBaseIds);
        }
        return jdbcTemplate.query(sql, params, rowMapper());
    }

    @Override
    public List<KnowledgeChunk> listByDocumentId(Long documentId) {
        String sql = """
                SELECT id, knowledge_base_id, document_id, chunk_index, content,
                       metadata::text AS metadata_json, created_at
                  FROM t_knowledge_chunk
                 WHERE document_id = :documentId
                   AND deleted = false
                 ORDER BY chunk_index ASC
                """;
        return jdbcTemplate.query(sql, Map.of("documentId", documentId.toString()), chunkRowMapper());
    }

    @Override
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        String sql = """
                UPDATE t_knowledge_chunk
                   SET deleted = true,
                       updated_at = now()
                 WHERE knowledge_base_id = :knowledgeBaseId
                   AND deleted = false
                """;
        jdbcTemplate.update(sql, Map.of("knowledgeBaseId", knowledgeBaseId));
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        String sql = """
                UPDATE t_knowledge_chunk
                   SET deleted = true,
                       updated_at = now()
                 WHERE document_id = :documentId
                   AND deleted = false
                """;
        jdbcTemplate.update(sql, Map.of("documentId", documentId.toString()));
    }

    private static RowMapper<KnowledgeSearchHit> rowMapper() {
        return (rs, rowNum) -> {
            KnowledgeSearchHit hit = new KnowledgeSearchHit();
            hit.setId(rs.getLong("id"));
            hit.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
            hit.setDocumentId(rs.getString("document_id"));
            hit.setChunkIndex(rs.getInt("chunk_index"));
            hit.setContent(rs.getString("content"));
            hit.setMetadataJson(rs.getString("metadata_json"));
            hit.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            hit.setScore(rs.getDouble("score"));
            return hit;
        };
    }

    private static RowMapper<KnowledgeChunk> chunkRowMapper() {
        return (rs, rowNum) -> {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
            chunk.setDocumentId(rs.getString("document_id"));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setContent(rs.getString("content"));
            chunk.setMetadataJson(rs.getString("metadata_json"));
            chunk.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return chunk;
        };
    }

    private static String toVectorLiteral(List<Double> vector) {
        return vector.stream()
                .map(value -> Double.toString(value))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
