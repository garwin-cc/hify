package com.hify.model.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LlmUsageQuery {

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long providerId;
    private Long modelConfigId;
    private Long userId;
    private Long projectId;
    private Long appId;
    private String groupBy;
}
