package com.hify.app;

import com.hify.support.HifyMockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "hify.init.default-provider.enabled=true",
        "hify.init.default-provider.name=Mock Default Provider",
        "hify.init.default-provider.type=OPENAI",
        "hify.init.default-provider.base-url=https://api.mock.local/v1",
        "hify.init.default-provider.api-key=sk-mock-default",
        "hify.init.default-provider.chat-model-name=Mock Chat",
        "hify.init.default-provider.chat-model-id=mock-chat",
        "hify.init.default-provider.embedding-model-name=Mock Embedding",
        "hify.init.default-provider.embedding-model-id=mock-embedding"
})
class SystemInitializationRunnerTest extends HifyMockIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_initializeDefaultProviderModelsPoliciesAndQuota_when_enabled() {
        Integer providerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_provider WHERE name = 'Mock Default Provider' AND deleted = 0",
                Integer.class);
        Integer modelCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_model_config WHERE model_id IN ('mock-chat', 'mock-embedding') AND deleted = 0",
                Integer.class);
        Integer policyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_model_default_policy WHERE scope_type = 'GLOBAL' AND enabled = 1 AND deleted = 0",
                Integer.class);
        Integer quotaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_rate_limit_quota WHERE scope_type = 'GLOBAL' AND enabled = 1 AND deleted = 0",
                Integer.class);

        assertThat(providerCount).isEqualTo(1);
        assertThat(modelCount).isEqualTo(2);
        assertThat(policyCount).isEqualTo(2);
        assertThat(quotaCount).isGreaterThanOrEqualTo(5);
    }
}
