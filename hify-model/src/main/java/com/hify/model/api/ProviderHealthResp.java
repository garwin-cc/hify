package com.hify.model.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderHealthResp {
    private String status;
    private LocalDateTime lastCheckAt;
    private LocalDateTime lastSuccessAt;
    private Integer failCount;
    private Integer latencyMs;
    private String errorMessage;
    private LocalDateTime updatedAt;
}
