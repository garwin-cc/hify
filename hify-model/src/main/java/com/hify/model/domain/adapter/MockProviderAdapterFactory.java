package com.hify.model.domain.adapter;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Primary
@Profile("mock")
@Component
public class MockProviderAdapterFactory extends ProviderAdapterFactory {

    private final ProviderAdapter mockProviderAdapter = new MockProviderAdapter();

    public MockProviderAdapterFactory() {
        super(null, null, null, null);
    }

    @Override
    public ProviderAdapter getAdapter(String type) {
        return mockProviderAdapter;
    }
}
