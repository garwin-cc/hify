package com.hify.support;

import com.hify.common.ratelimit.RateLimitResult;
import com.hify.common.ratelimit.RateLimitRule;
import com.hify.common.ratelimit.RateLimitService;
import com.hify.knowledge.domain.KnowledgeChunk;
import com.hify.knowledge.domain.KnowledgeSearchHit;
import com.hify.knowledge.domain.KnowledgeVectorRepository;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Transactional
@Rollback
@Import(HifyMockIntegrationTest.MockVectorRepositoryConfig.class)
public abstract class HifyMockIntegrationTest {

    @TestConfiguration
    public static class MockVectorRepositoryConfig {
        @Bean
        @Primary
        KnowledgeVectorRepository knowledgeVectorRepository() {
            return new KnowledgeVectorRepository() {
                @Override
                public Long upsert(KnowledgeChunk chunk, List<Double> embedding) {
                    return 0L;
                }

                @Override
                public void saveDocumentChunks(List<KnowledgeChunk> chunks) {
                }

                @Override
                public List<KnowledgeSearchHit> search(List<Long> knowledgeBaseIds, List<Double> queryEmbedding, int topK) {
                    return List.of();
                }

                @Override
                public List<KnowledgeChunk> listByDocumentId(Long documentId) {
                    return List.of();
                }

                @Override
                public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
                }

                @Override
                public void deleteByDocumentId(Long documentId) {
                }
            };
        }

        @Bean
        @Primary
        RateLimitService rateLimitService() {
            return new RateLimitService() {
                @Override
                public RateLimitResult check(RateLimitRule rule) {
                    return RateLimitResult.allowed(Long.MAX_VALUE);
                }
            };
        }

        @Bean("pgvectorJdbcTemplate")
        JdbcTemplate pgvectorJdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
