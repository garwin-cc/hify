package com.hify.model.domain;

import com.hify.model.api.ConnectivityTestResult;
import com.hify.model.domain.adapter.ProviderAdapter;
import com.hify.model.domain.adapter.ProviderAdapterFactory;
import com.hify.model.infra.ProviderHealthMapper;
import com.hify.model.infra.ProviderHealthPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderHealthCheckJobTest {

    @Mock
    private ProviderMapper providerMapper;
    @Mock
    private ProviderHealthMapper providerHealthMapper;
    @Mock
    private ProviderAdapterFactory providerAdapterFactory;
    @Mock
    private ProviderAdapter providerAdapter;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private ThreadPoolExecutor asyncExecutor;
    @Mock
    private ProviderHealthAlertNotifier alertNotifier;

    @Test
    void checkAllSendsAlertWhenProviderBecomesDown() {
        ProviderPo provider = new ProviderPo();
        provider.setId(7L);
        provider.setName("OpenAI");
        provider.setType("OPENAI");
        provider.setEnabled(1);
        ProviderHealthPo health = new ProviderHealthPo();
        health.setProviderId(7L);
        health.setStatus("DEGRADED");
        health.setFailCount(2);
        health.setSuccessCount(0);
        health.setTotalCheckCount(2);
        health.setAlertStatus("OK");

        when(providerMapper.selectList(any())).thenReturn(List.of(provider));
        when(providerAdapterFactory.getAdapter("OPENAI")).thenReturn(providerAdapter);
        when(providerAdapter.testConnection(provider)).thenReturn(ConnectivityTestResult.failure(1000, "timeout"));
        when(providerHealthMapper.selectByProviderId(7L)).thenReturn(health);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(asyncExecutor).execute(any(Runnable.class));

        ProviderHealthCheckJob job = new ProviderHealthCheckJob(providerMapper, providerHealthMapper,
                providerAdapterFactory, cacheManager, asyncExecutor);
        job.setAlertNotifier(alertNotifier);

        job.checkAll();

        ArgumentCaptor<ProviderHealthAlertEvent> captor = ArgumentCaptor.forClass(ProviderHealthAlertEvent.class);
        verify(alertNotifier).notify(captor.capture());
        assertThat(captor.getValue().providerId()).isEqualTo(7L);
        assertThat(captor.getValue().providerName()).isEqualTo("OpenAI");
        assertThat(captor.getValue().status()).isEqualTo("DOWN");
        assertThat(captor.getValue().errorMessage()).isEqualTo("timeout");
    }
}
