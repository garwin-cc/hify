package com.hify.model.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderHealthResp {
    private String status;
    private LocalDateTime lastCheckAt;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastErrorAt;
    private LocalDateTime lastAlertAt;
    private String alertStatus;
    private Integer failCount;
    private Integer successCount;
    private Integer totalCheckCount;
    private Integer latencyMs;
    private String errorMessage;
    private LocalDateTime updatedAt;
}
