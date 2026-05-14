package com.hify.common.ratelimit;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Around("@annotation(rateLimited)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        RateLimitResult result = rateLimitService.check(RateLimitRule.builder()
                .dimension(rateLimited.dimension())
                .key(resolveKey(joinPoint, rateLimited))
                .limit(rateLimited.limit())
                .window(Duration.ofSeconds(rateLimited.windowSeconds()))
                .failOpen(rateLimited.failOpen())
                .build());
        if (!result.isAllowed()) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return joinPoint.proceed();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimited rateLimited) {
        if (StringUtils.hasText(rateLimited.key())) {
            return rateLimited.key();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }
}
