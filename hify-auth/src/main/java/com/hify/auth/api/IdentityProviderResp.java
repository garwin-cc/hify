package com.hify.auth.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IdentityProviderResp {

    private Long id;
    private String name;
    private String type;
    private String issuerUrl;
    private String clientId;
    private String ldapUrl;
    private String ldapBaseDn;
    private Integer enabled;
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
