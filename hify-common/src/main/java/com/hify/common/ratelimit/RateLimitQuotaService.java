package com.hify.common.ratelimit;

import java.time.Duration;

public interface RateLimitQuotaService {

    RateLimitRule resolve(RateLimitDimension dimension, String key, int fallbackLimit,
                          Duration fallbackWindow, boolean fallbackFailOpen);
}
