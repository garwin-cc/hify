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
    private int runtimeLogRetentionDays = 90;
    private int auditLogRetentionDays = 180;
    private int jobLogRetentionDays = 30;
    private boolean archiveEnabled = true;
    private int archiveBatchSize = 500;
    private int archiveRetentionDays = 365;
    private int knowledgeProcessingTimeoutMinutes = 60;
    private int workflowRunTimeoutGraceMinutes = 0;
}
