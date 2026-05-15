package com.hify.app.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.app.infra.RateLimitQuotaMapper;
import com.hify.common.ratelimit.RateLimitDimension;
import com.hify.common.ratelimit.RateLimitQuotaService;
import com.hify.common.ratelimit.RateLimitRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DatabaseRateLimitQuotaService implements RateLimitQuotaService {

    private static final String GLOBAL_SCOPE = "GLOBAL";
    private static final Long GLOBAL_SCOPE_ID = 0L;
    private static final String WILDCARD_KEY = "*";

    private final RateLimitQuotaMapper quotaMapper;

    @Override
    public RateLimitRule resolve(RateLimitDimension dimension, String key, int fallbackLimit,
                                 Duration fallbackWindow, boolean fallbackFailOpen) {
        String normalizedKey = StringUtils.hasText(key) ? key : WILDCARD_KEY;
        List<RateLimitQuotaPo> rows = quotaMapper.selectList(Wrappers.lambdaQuery(RateLimitQuotaPo.class)
                .eq(RateLimitQuotaPo::getScopeType, GLOBAL_SCOPE)
                .eq(RateLimitQuotaPo::getScopeId, GLOBAL_SCOPE_ID)
                .eq(RateLimitQuotaPo::getDimension, dimension.name())
                .in(RateLimitQuotaPo::getQuotaKey, List.of(normalizedKey, WILDCARD_KEY))
                .eq(RateLimitQuotaPo::getEnabled, 1)
                .eq(RateLimitQuotaPo::getDeleted, 0));
        RateLimitQuotaPo quota = chooseQuota(rows, normalizedKey);
        if (quota == null) {
            return fallbackRule(dimension, normalizedKey, fallbackLimit, fallbackWindow, fallbackFailOpen);
        }
        return RateLimitRule.builder()
                .dimension(dimension)
                .key(normalizedKey)
                .limit(positiveOrDefault(quota.getLimitCount(), fallbackLimit))
                .window(Duration.ofSeconds(positiveOrDefault(quota.getWindowSec(), (int) fallbackWindow.toSeconds())))
                .failOpen(quota.getFailOpen() == null || quota.getFailOpen() == 1)
                .build();
    }

    private static RateLimitQuotaPo chooseQuota(List<RateLimitQuotaPo> rows, String key) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        RateLimitQuotaPo wildcard = null;
        for (RateLimitQuotaPo row : rows) {
            if (key.equals(row.getQuotaKey())) {
                return row;
            }
            if (wildcard == null && WILDCARD_KEY.equals(row.getQuotaKey())) {
                wildcard = row;
            }
        }
        return wildcard;
    }

    private static RateLimitRule fallbackRule(RateLimitDimension dimension, String key, int limit,
                                              Duration window, boolean failOpen) {
        return RateLimitRule.builder()
                .dimension(dimension)
                .key(key)
                .limit(limit)
                .window(window)
                .failOpen(failOpen)
                .build();
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
