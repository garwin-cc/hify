package com.hify.model.domain;

public record ProviderHealthAlertEvent(
        Long providerId,
        String providerName,
        String providerType,
        String status,
        Integer failCount,
        Integer latencyMs,
        String errorMessage
) {
}
