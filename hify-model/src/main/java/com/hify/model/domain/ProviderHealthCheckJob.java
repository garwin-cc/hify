package com.hify.model.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.model.api.ConnectivityTestResult;
import com.hify.model.domain.adapter.ProviderAdapterFactory;
import com.hify.model.infra.ProviderHealthMapper;
import com.hify.model.infra.ProviderHealthPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
public class ProviderHealthCheckJob {

    private static final int FAIL_THRESHOLD = 3;

    private final ProviderMapper         providerMapper;
    private final ProviderHealthMapper   providerHealthMapper;
    private final ProviderAdapterFactory providerAdapterFactory;
    private final CacheManager           cacheManager;
    private final ThreadPoolExecutor     asyncExecutor;

    public ProviderHealthCheckJob(ProviderMapper providerMapper,
                                  ProviderHealthMapper providerHealthMapper,
                                  ProviderAdapterFactory providerAdapterFactory,
                                  CacheManager cacheManager,
                                  @Qualifier("asyncExecutor") ThreadPoolExecutor asyncExecutor) {
        this.providerMapper         = providerMapper;
        this.providerHealthMapper   = providerHealthMapper;
        this.providerAdapterFactory = providerAdapterFactory;
        this.cacheManager           = cacheManager;
        this.asyncExecutor          = asyncExecutor;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void checkAll() {
        List<ProviderPo> providers = providerMapper.selectList(
                new LambdaQueryWrapper<ProviderPo>().eq(ProviderPo::getEnabled, 1));

        if (providers.isEmpty()) return;

        log.info("health check start, {} enabled provider(s)", providers.size());
        for (ProviderPo provider : providers) {
            asyncExecutor.execute(() -> checkOne(provider));
        }
    }

    private void checkOne(ProviderPo provider) {
        Long id = provider.getId();
        try {
            ConnectivityTestResult result =
                    providerAdapterFactory.getAdapter(provider.getType()).testConnection(provider);
            updateHealth(id, result);
            evictDetailCache(id);
            log.info("health check id={} type={} success={} latency={}ms",
                    id, provider.getType(), result.isSuccess(), result.getLatencyMs());
        } catch (Exception e) {
            log.error("health check unexpected error id={}: {}", id, e.getMessage(), e);
        }
    }

    // ── 健康状态更新（fail_count 累计，连续 3 次才标记 DOWN）────────────

    private void updateHealth(Long providerId, ConnectivityTestResult result) {
        LocalDateTime now = LocalDateTime.now();

        ProviderHealthPo health = providerHealthMapper.selectByProviderId(providerId);
        if (health == null) {
            health = new ProviderHealthPo();
            health.setProviderId(providerId);
            health.setStatus("UNKNOWN");
            health.setFailCount(0);
        }

        health.setLastCheckAt(now);
        health.setLatencyMs(result.getLatencyMs());
        health.setUpdatedAt(now);

        if (result.isSuccess()) {
            health.setStatus("UP");
            health.setLastSuccessAt(now);
            health.setFailCount(0);
            health.setErrorMessage("");
        } else {
            int failCount = (health.getFailCount() == null ? 0 : health.getFailCount()) + 1;
            health.setFailCount(failCount);
            health.setErrorMessage(result.getErrorMessage());
            if (failCount >= FAIL_THRESHOLD) {
                health.setStatus("DOWN");
            }
        }

        providerHealthMapper.upsert(health);
    }

    private void evictDetailCache(Long providerId) {
        Cache cache = cacheManager.getCache("provider-cache");
        if (cache != null) {
            cache.evict("detail:" + providerId);
        }
    }
}
