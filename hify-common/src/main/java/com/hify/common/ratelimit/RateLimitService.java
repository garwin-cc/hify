package com.hify.common.ratelimit;

public interface RateLimitService {

    RateLimitResult check(RateLimitRule rule);
}
