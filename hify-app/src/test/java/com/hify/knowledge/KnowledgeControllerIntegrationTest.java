package com.hify.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.support.HifyMockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeControllerIntegrationTest extends HifyMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Sql(statements = {
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (8101, 'Knowledge IT Provider', 'OPENAI', '', '{}', 1, 0, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted) VALUES (8201, 8101, 'BGE Reranker', 'bge-reranker', 'RERANK', 8192, '{}', 1, 0, 0, 0)",
            "INSERT INTO t_knowledge_base (id, workspace_id, project_id, visibility, share_scope, name, description, embedding_model_config_id, enabled, document_count, chunk_count, retrieval_mode, hybrid_alpha, top_k, candidate_top_k, score_threshold, chunk_size, chunk_overlap, max_context_tokens, rerank_enabled, rerank_model_config_id, rerank_top_n, metadata_filter_enabled, default_metadata_filter_json, active_index_version, index_status, created_by, deleted) VALUES (8301, 1, 1, 'PROJECT', 'PROJECT', 'Knowledge IT', '', 8201, 1, 0, 0, 'VECTOR', 0.7000, 5, 20, 0.65000, 512, 64, 3000, 0, NULL, 20, 0, '{}', 1, 'READY', 0, 0)"
    })
    void should_persistRagQualityConfig_when_updateRetrievalConfig() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "retrievalMode", "HYBRID",
                "hybridAlpha", 0.35,
                "topK", 4,
                "candidateTopK", 30,
                "scoreThreshold", 0.2,
                "rerankEnabled", 1,
                "rerankModelConfigId", 8201,
                "rerankTopN", 12,
                "metadataFilterEnabled", 1,
                "defaultMetadataFilter", Map.of(
                        "department", "finance",
                        "tags", java.util.List.of("policy", "expense")
                )
        ));

        String response = mockMvc.perform(put("/api/v1/knowledge-bases/8301/retrieval-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT retrieval_mode, hybrid_alpha, top_k, candidate_top_k, score_threshold,
                       rerank_enabled, rerank_model_config_id, rerank_top_n,
                       metadata_filter_enabled, default_metadata_filter_json
                  FROM t_knowledge_base
                 WHERE id = 8301
                """);

        assertThat(root.path("code").asInt()).isEqualTo(200);
        assertThat(row.get("retrieval_mode")).isEqualTo("HYBRID");
        assertThat(((Number) row.get("top_k")).intValue()).isEqualTo(4);
        assertThat(((Number) row.get("candidate_top_k")).intValue()).isEqualTo(30);
        assertThat(((Number) row.get("rerank_enabled")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("rerank_model_config_id")).longValue()).isEqualTo(8201L);
        assertThat(((Number) row.get("rerank_top_n")).intValue()).isEqualTo(12);
        assertThat(((Number) row.get("metadata_filter_enabled")).intValue()).isEqualTo(1);
        assertThat(String.valueOf(row.get("default_metadata_filter_json"))).contains("\"department\":\"finance\"");
    }
}
