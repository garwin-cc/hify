package com.hify.auth.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.auth.api.UserRole;
import com.hify.auth.api.UserStatus;
import com.hify.auth.infra.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitAdminRunner implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;

    @Value("${hify.auth.init-admin.username:admin}")
    private String username;

    @Value("${hify.auth.init-admin.password:}")
    private String password;

    @Value("${hify.auth.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        Long adminCount = userMapper.selectCount(Wrappers.lambdaQuery(UserPo.class)
                .eq(UserPo::getRole, UserRole.ADMIN.name()));
        if (adminCount != null && adminCount > 0) {
            return;
        }
        if (!StringUtils.hasText(password)) {
            log.warn("no admin user exists and hify.auth.init-admin.password is empty; skip init admin creation");
            return;
        }
        UserPo user = new UserPo();
        user.setUsername(username);
        user.setDisplayName("管理员");
        user.setPasswordHash(passwordHasher.hash(password));
        user.setRole(UserRole.ADMIN.name());
        user.setStatus(UserStatus.ACTIVE.name());
        userMapper.insert(user);
        log.info("initialized admin user username={}", username);
    }
}
