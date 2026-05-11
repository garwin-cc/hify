package com.hify.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_user_session")
@EqualsAndHashCode(callSuper = false)
public class UserSessionPo extends BaseEntity {

    private Long userId;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private Boolean revoked;
}
