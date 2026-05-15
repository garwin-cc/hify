package com.hify.app.domain;

import com.hify.app.infra.RateLimitQuotaMapper;
import com.hify.common.ratelimit.RateLimitDimension;
import com.hify.common.ratelimit.RateLimitRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseRateLimitQuotaServiceTest {

    @Mock
    private RateLimitQuotaMapper quotaMapper;

    @Test
    void resolvesExactQuotaBeforeWildcardQuota() {
        RateLimitQuotaPo wildcard = quota("*", 60, 60, 1);
        RateLimitQuotaPo exact = quota("42", 5, 10, 0);
        when(quotaMapper.selectList(any())).thenReturn(List.of(wildcard, exact));
        DatabaseRateLimitQuotaService service = new DatabaseRateLimitQuotaService(quotaMapper);

        RateLimitRule rule = service.resolve(RateLimitDimension.USER, "42", 100, Duration.ofMinutes(1), true);

        assertThat(rule.getDimension()).isEqualTo(RateLimitDimension.USER);
        assertThat(rule.getKey()).isEqualTo("42");
        assertThat(rule.getLimit()).isEqualTo(5);
        assertThat(rule.getWindow()).isEqualTo(Duration.ofSeconds(10));
        assertThat(rule.isFailOpen()).isFalse();
    }

    @Test
    void fallsBackWhenNoEnabledQuotaExists() {
        when(quotaMapper.selectList(any())).thenReturn(List.of());
        DatabaseRateLimitQuotaService service = new DatabaseRateLimitQuotaService(quotaMapper);

        RateLimitRule rule = service.resolve(RateLimitDimension.MODEL, "gpt-4o", 600, Duration.ofSeconds(30), true);

        assertThat(rule.getDimension()).isEqualTo(RateLimitDimension.MODEL);
        assertThat(rule.getKey()).isEqualTo("gpt-4o");
        assertThat(rule.getLimit()).isEqualTo(600);
        assertThat(rule.getWindow()).isEqualTo(Duration.ofSeconds(30));
        assertThat(rule.isFailOpen()).isTrue();
    }

    private static RateLimitQuotaPo quota(String quotaKey, int limit, int windowSec, int failOpen) {
        RateLimitQuotaPo po = new RateLimitQuotaPo();
        po.setScopeType("GLOBAL");
        po.setScopeId(0L);
        po.setDimension("USER");
        po.setQuotaKey(quotaKey);
        po.setLimitCount(limit);
        po.setWindowSec(windowSec);
        po.setFailOpen(failOpen);
        po.setEnabled(1);
        return po;
    }
}
