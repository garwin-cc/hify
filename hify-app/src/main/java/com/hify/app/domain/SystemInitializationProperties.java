package com.hify.app.domain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hify.init")
public class SystemInitializationProperties {

    private DefaultProvider defaultProvider = new DefaultProvider();

    @Data
    public static class DefaultProvider {
        private boolean enabled = false;
        private String name = "Default Provider";
        private String type = "OPENAI";
        private String baseUrl = "";
        private String apiKey = "";
        private String chatModelName = "Default Chat Model";
        private String chatModelId = "";
        private String embeddingModelName = "Default Embedding Model";
        private String embeddingModelId = "";
    }
}
