package com.hify.common.resilience;

import com.hify.common.http.LlmApiException;
import com.hify.common.metrics.HifyMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * LLM 熔断 + 重试门面。
 *
 * <p>每个 providerName 持有独立的熔断器实例（共享 application.yml default 配置）。
 * 重试策略按 {@link LlmApiException.Type} 区分：
 * <ul>
 *   <li>TIMEOUT：重试 2 次，固定间隔 1s</li>
 *   <li>RATE_LIMITED：重试 2 次，退避间隔 2s / 4s</li>
 *   <li>AUTH_FAILED：不重试（立即失败）</li>
 * </ul>
 *
 * <p>装饰顺序：CB（外层）→ Retry（内层）→ 实际调用。
 * CB 只感知全部重试结束后的最终结果，避免单次网络抖动触发熔断计数。
 *
 * <p>典型用法：
 * <pre>
 *   String resp = circuitBreakerService.execute("openai",
 *       () -> llmHttpClient.post(url, headers, body));
 * </pre>
 */
@Slf4j
@Service
public class CircuitBreakerService {

    private final CircuitBreakerRegistry cbRegistry;
    private final Retry llmRetry;
    private final HifyMetrics hifyMetrics;

    public CircuitBreakerService(CircuitBreakerRegistry cbRegistry, HifyMetrics hifyMetrics) {
        this.cbRegistry = cbRegistry;
        this.hifyMetrics = hifyMetrics;
        this.llmRetry = buildRetry();
    }

    /**
     * 按 providerName 获取（或懒创建）熔断器实例。
     * 首次调用时自动注册，state transition 事件同时写入日志。
     */
    public CircuitBreaker getCircuitBreaker(String providerName) {
        CircuitBreaker cb = cbRegistry.circuitBreaker(providerName);
        hifyMetrics.registerCircuitBreakerState(providerName, cb);
        // 只对新建实例注册监听（Registry 保证相同名字只创建一次）
        cb.getEventPublisher()
                .onStateTransition(e -> log.warn(
                        "CB [{}] {} -> {}",
                        providerName,
                        e.getStateTransition().getFromState(),
                        e.getStateTransition().getToState()))
                .onCallNotPermitted(e -> log.warn(
                        "CB [{}] OPEN，请求被拒绝", providerName));
        return cb;
    }

    /**
     * 用熔断器 + 重试执行 supplier。
     *
     * @param providerName LLM 提供商名称，对应独立熔断器实例
     * @param supplier     实际调用逻辑（通常是 LlmHttpClient.post / .stream）
     * @throws LlmApiException         TIMEOUT / RATE_LIMITED，重试耗尽后透传
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException 熔断器 OPEN
     */
    public <T> T execute(String providerName, Supplier<T> supplier) {
        CircuitBreaker cb = getCircuitBreaker(providerName);
        // CB 在外，Retry 在内：CB 只看最终结果，不计单次重试失败
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(cb,
                Retry.decorateSupplier(llmRetry, supplier));
        return decorated.get();
    }

    // ------------------------------------------------------------------ 私有方法

    private static Retry buildRetry() {
        RetryConfig config = RetryConfig.custom()
                // 1 次初始调用 + 最多 2 次重试
                .maxAttempts(3)
                // AUTH_FAILED 不重试；其余 LlmApiException 及网络 IO 异常重试
                .retryOnException(CircuitBreakerService::shouldRetry)
                // 根据异常类型和重试次数决定等待时长
                .intervalBiFunction((attempt, either) -> waitMs(attempt, either.getLeft()))
                .build();

        Retry retry = Retry.of("llm-retry", config);
        retry.getEventPublisher()
                .onRetry(e -> log.warn(
                        "LLM retry attempt={} cause={}",
                        e.getNumberOfRetryAttempts(),
                        e.getLastThrowable().getMessage()))
                .onError(e -> log.error(
                        "LLM retry exhausted after {} attempts, last={}",
                        e.getNumberOfRetryAttempts(),
                        e.getLastThrowable().getMessage()));
        return retry;
    }

    private static boolean shouldRetry(Throwable e) {
        if (e instanceof LlmApiException ex) {
            return ex.getType() == LlmApiException.Type.TIMEOUT
                    || ex.getType() == LlmApiException.Type.RATE_LIMITED;
        }
        // 非 LlmApiException（如 IO 异常）也重试，防止偶发网络抖动
        return true;
    }

    /**
     * 重试等待时长（毫秒）。
     *
     * <ul>
     *   <li>RATE_LIMITED：2^attempt 秒（attempt=1→2s，attempt=2→4s）</li>
     *   <li>TIMEOUT 及其他：固定 1s</li>
     * </ul>
     */
    private static long waitMs(int attempt, Throwable cause) {
        if (cause instanceof LlmApiException ex
                && ex.getType() == LlmApiException.Type.RATE_LIMITED) {
            return Duration.ofSeconds((long) Math.pow(2, attempt)).toMillis();
        }
        return Duration.ofSeconds(1).toMillis();
    }
}
