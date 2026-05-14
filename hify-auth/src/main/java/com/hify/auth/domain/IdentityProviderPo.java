package com.hify.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_identity_provider")
public class IdentityProviderPo {

    private Long id;
    private String name;
    private String type;
    private String issuerUrl;
    private String clientId;
    private String clientSecret;
    private String ldapUrl;
    private String ldapBaseDn;
    private Integer enabled;
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
