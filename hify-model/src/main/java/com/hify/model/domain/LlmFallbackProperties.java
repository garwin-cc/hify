package com.hify.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "hify.llm")
public class LlmFallbackProperties {

    private Map<String, String> fallback = new HashMap<>();

    public String fallbackType(String providerType) {
        if (providerType == null || fallback == null) {
            return null;
        }
        return fallback.get(providerType.toLowerCase()) != null
                ? fallback.get(providerType.toLowerCase())
                : fallback.get(providerType.toUpperCase());
    }
}
