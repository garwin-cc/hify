package com.hify.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimitService implements RateLimitService {

    private static final String KEY_PREFIX = "hify:rl:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public RateLimitResult check(RateLimitRule rule) {
        if (rule == null || rule.getDimension() == null || rule.getKey() == null || rule.getKey().isBlank()) {
            return RateLimitResult.allowed(Long.MAX_VALUE);
        }
        int limit = rule.getLimit() <= 0 ? 1 : rule.getLimit();
        Duration window = rule.getWindow() == null || rule.getWindow().isZero() || rule.getWindow().isNegative()
                ? Duration.ofMinutes(1)
                : rule.getWindow();
        String key = KEY_PREFIX + rule.getDimension().name().toLowerCase() + ":" + rule.getKey();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, window);
            }
            long current = count == null ? 1 : count;
            if (current > limit) {
                Long ttl = redisTemplate.getExpire(key);
                return RateLimitResult.rejected(ttl == null || ttl < 0 ? window.toSeconds() : ttl);
            }
            return RateLimitResult.allowed(Math.max(0, limit - current));
        } catch (Exception e) {
            log.warn("rate limit check failed dimension={} key={} message={}",
                    rule.getDimension(), rule.getKey(), e.getMessage());
            return rule.isFailOpen() ? RateLimitResult.allowed(Long.MAX_VALUE)
                    : RateLimitResult.rejected(window.toSeconds());
        }
    }
}
