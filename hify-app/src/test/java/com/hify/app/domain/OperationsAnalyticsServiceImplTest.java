package com.hify.app.domain;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationsAnalyticsServiceImplTest {

    @Test
    void should_useMysqlNamedJdbcTemplate_when_pgvectorNamedJdbcTemplateAlsoExists() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class, OperationsAnalyticsServiceImpl.class);
        context.refresh();
        try (context) {
            OperationsAnalyticsService service = context.getBean(OperationsAnalyticsService.class);

            service.overview(null, null, null);

            verify(context.getBean("mysqlNamedJdbcTemplate", NamedParameterJdbcTemplate.class), atLeastOnce())
                    .queryForMap(anyString(), any(SqlParameterSource.class));
            verifyNoInteractions(context.getBean("pgvectorNamedJdbcTemplate", NamedParameterJdbcTemplate.class));
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        NamedParameterJdbcTemplate mysqlNamedJdbcTemplate() {
            NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
            when(template.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(Map.of(
                    "conversation_count", 0,
                    "failed_count", 0,
                    "rag_triggered_count", 0,
                    "run_count", 0,
                    "success_count", 0,
                    "call_count", 0,
                    "failure_count", 0
            ));
            when(template.queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class))).thenReturn(0L);
            when(template.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(java.util.List.of());
            return template;
        }

        @Bean
        NamedParameterJdbcTemplate pgvectorNamedJdbcTemplate() {
            return mock(NamedParameterJdbcTemplate.class);
        }
    }
}
