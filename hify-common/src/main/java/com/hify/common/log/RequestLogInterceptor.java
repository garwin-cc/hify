package com.hify.common.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求日志拦截器。
 *
 * <ul>
 *   <li>preHandle：traceId 注入 MDC（{@link com.hify.common.filter.TraceIdFilter}
 *       已存在时复用，否则生成）；记录请求开始时间戳</li>
 *   <li>afterCompletion：记录 method / path / status / 耗时；超过 1s 打 WARN</li>
 * </ul>
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME   = "__startTime";
    private static final String TRACE_OWNER  = "__traceOwner"; // 标记 traceId 由本拦截器生成

    private static final long SLOW_THRESHOLD_MS = 1_000;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // TraceIdFilter 已运行时复用其 traceId；否则自行生成
        if (MDC.get(TraceContext.TRACE_ID_KEY) == null) {
            MDC.put(TraceContext.TRACE_ID_KEY, TraceContext.generateTraceId());
            request.setAttribute(TRACE_OWNER, Boolean.TRUE);
        }
        request.setAttribute(START_TIME, System.currentTimeMillis());
        log.info("request enter method={} path={}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Long start = (Long) request.getAttribute(START_TIME);
        long elapsed = start != null ? System.currentTimeMillis() - start : 0;
        int status = response.getStatus();

        if (elapsed > SLOW_THRESHOLD_MS) {
            log.warn("[SLOW] {} {} {} {}ms", request.getMethod(),
                    request.getRequestURI(), status, elapsed);
        } else {
            log.info("{} {} {} {}ms", request.getMethod(),
                    request.getRequestURI(), status, elapsed);
        }

        // 只清理本拦截器创建的 traceId；TraceIdFilter 创建的由其自身清理
        if (Boolean.TRUE.equals(request.getAttribute(TRACE_OWNER))) {
            MDC.remove(TraceContext.TRACE_ID_KEY);
        }
    }
}
