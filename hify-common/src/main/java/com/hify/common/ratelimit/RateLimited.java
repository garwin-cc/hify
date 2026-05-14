package com.hify.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    RateLimitDimension dimension() default RateLimitDimension.USER;

    String key() default "";

    int limit();

    int windowSeconds() default 60;

    boolean failOpen() default true;
}
