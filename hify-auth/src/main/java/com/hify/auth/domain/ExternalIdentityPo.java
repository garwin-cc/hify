package com.hify.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_external_identity")
public class ExternalIdentityPo {

    private Long id;
    private Long userId;
    private Long identityProviderId;
    private String providerType;
    private String externalSubject;
    private String externalUsername;
    private String email;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
