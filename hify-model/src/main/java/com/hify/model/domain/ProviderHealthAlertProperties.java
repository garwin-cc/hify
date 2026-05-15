package com.hify.model.domain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hify.provider-health.alert")
public class ProviderHealthAlertProperties {

    private boolean enabled = false;
    private String webhookUrl = "";
    private int timeoutSeconds = 3;
}
