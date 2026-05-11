package com.hify.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
@EqualsAndHashCode(callSuper = false)
public class UserPo extends BaseEntity {

    private String username;

    private String displayName;

    private String passwordHash;

    private String role;

    private String status;

    private LocalDateTime lastLoginAt;
}
