package com.hify.app.domain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hify.jobs")
public class AppMaintenanceProperties {

    private boolean enabled = true;
    private int logRetentionDays = 90;
    private int jobLogRetentionDays = 30;
}
