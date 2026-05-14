package com.hify.common.ratelimit;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

@Data
@Builder
public class RateLimitRule {

    private RateLimitDimension dimension;
    private String key;
    private int limit;
    private Duration window;
    @Builder.Default
    private boolean failOpen = true;
}
