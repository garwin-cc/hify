package com.hify.model.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderResp {
    private Long id;
    private String name;
    private String type;
    private String baseUrl;
    private Integer enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
