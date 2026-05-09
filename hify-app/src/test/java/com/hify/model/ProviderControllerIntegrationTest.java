package com.hify.model;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProviderControllerIntegrationTest extends HifyMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Sql(statements = "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1001, 'Existing Create Seed', 'OPENAI', '', '{\"apiKey\":\"sk-seed-token\"}', 1, 0, 0, 0)")
    void should_createProviderAndReturnId_when_requestIsValid() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "OpenAI Create",
                "type", "openai",
                "baseUrl", "https://api.openai.com",
                "apiKey", "sk-valid-create-token",
                "sortOrder", 5
        ));

        String response = mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        Long id = root.path("data").path("id").asLong();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_provider WHERE id = ? AND name = ? AND deleted = 0",
                Integer.class, id, "OpenAI Create");

        assertThat(root.path("code").asInt()).isEqualTo(200);
        assertThat(id).isPositive();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Sql(statements = "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1002, 'Duplicate Provider', 'OPENAI', '', '{\"apiKey\":\"sk-seed-token\"}', 1, 0, 0, 0)")
    void should_returnProviderNameDuplicateCode_when_providerNameAlreadyExists() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Duplicate Provider",
                "type", "openai",
                "apiKey", "sk-valid-duplicate-token"
        ));

        String response = mockMvc.perform(post("/api/v1/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_provider WHERE name = ? AND deleted = 0",
                Integer.class, "Duplicate Provider");

        assertThat(root.path("code").asInt()).isEqualTo(2001);
        assertThat(root.path("data").isNull()).isTrue();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Sql(statements = "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1003, 'Existing Provider', 'OPENAI_COMPATIBLE', 'https://llm.example.com', '{\"apiKey\":\"sk-seed-token\"}', 1, 9, 0, 0)")
    void should_returnProviderDetail_when_providerExists() throws Exception {
        String response = mockMvc.perform(get("/api/v1/providers/{id}", 1003L))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode provider = objectMapper.readTree(response).path("data").path("provider");

        assertThat(provider.path("id").asLong()).isEqualTo(1003L);
        assertThat(provider.path("name").asText()).isEqualTo("Existing Provider");
        assertThat(provider.path("type").asText()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(provider.path("baseUrl").asText()).isEqualTo("https://llm.example.com");
        assertThat(provider.path("enabled").asInt()).isEqualTo(1);
        assertThat(provider.path("sortOrder").asInt()).isEqualTo(9);
    }

    @Test
    @Sql(statements = "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1004, 'Unrelated Provider', 'OPENAI', '', '{\"apiKey\":\"sk-seed-token\"}', 1, 0, 0, 0)")
    void should_returnProviderNotFoundCode_when_providerDoesNotExist() throws Exception {
        String response = mockMvc.perform(get("/api/v1/providers/{id}", 999999L))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);

        assertThat(root.path("code").asInt()).isEqualTo(2000);
        assertThat(root.path("data").isNull()).isTrue();
    }

    @Test
    @Sql(statements = "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1005, 'Before Update', 'OPENAI', '', '{\"apiKey\":\"sk-seed-token\"}', 1, 0, 0, 0)")
    void should_updateProviderNameInDatabase_when_requestIsValid() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "After Update",
                "type", "openai",
                "baseUrl", "https://api.openai.com",
                "sortOrder", 3
        ));

        String response = mockMvc.perform(put("/api/v1/providers/{id}", 1005L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String storedName = jdbcTemplate.queryForObject(
                "SELECT name FROM t_provider WHERE id = ? AND deleted = 0",
                String.class, 1005L);

        assertThat(objectMapper.readTree(response).path("code").asInt()).isEqualTo(200);
        assertThat(storedName).isEqualTo("After Update");
    }

    @Test
    @Sql(statements = "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1006, 'Delete Provider', 'OPENAI', '', '{\"apiKey\":\"sk-seed-token\"}', 1, 0, 0, 0)")
    void should_markProviderDeletedInDatabase_when_providerIsDeleted() throws Exception {
        String response = mockMvc.perform(delete("/api/v1/providers/{id}", 1006L))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_provider WHERE id = ?",
                Integer.class, 1006L);

        assertThat(objectMapper.readTree(response).path("code").asInt()).isEqualTo(200);
        assertThat(deleted).isEqualTo(1);
    }
}
