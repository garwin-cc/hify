package com.hify.auth.domain;

import com.hify.auth.api.LoginReq;
import com.hify.auth.api.LoginResp;
import com.hify.auth.api.UserRole;
import com.hify.auth.api.UserStatus;
import com.hify.auth.infra.UserMapper;
import com.hify.auth.infra.UserSessionMapper;
import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceImplTest {

    @Test
    void loginCreatesSessionAndReturnsCurrentUser() {
        PasswordHasher passwordHasher = new PasswordHasher();
        UserPo user = activeUser(passwordHasher.hash("secret123"));
        List<UserSessionPo> sessions = new ArrayList<>();
        AuthServiceImpl service = newService(user, sessions, passwordHasher);

        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("secret123");

        LoginResp resp = service.login(req);

        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getUser().getUsername()).isEqualTo("admin");
        assertThat(resp.getUser().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getTokenHash()).isNotEqualTo(resp.getToken());
        assertThat(service.authenticate(resp.getToken()).getUsername()).isEqualTo("admin");
    }

    @Test
    void loginRejectsWrongPassword() {
        PasswordHasher passwordHasher = new PasswordHasher();
        AuthServiceImpl service = newService(activeUser(passwordHasher.hash("secret123")), new ArrayList<>(), passwordHasher);
        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("bad-password");

        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(BizException.class);
    }

    private AuthServiceImpl newService(UserPo user, List<UserSessionPo> sessions, PasswordHasher passwordHasher) {
        UserMapper userMapper = mapper(UserMapper.class, method -> {
            if ("selectOne".equals(method) || "selectById".equals(method)) {
                return args -> user;
            }
            return null;
        });
        UserSessionMapper sessionMapper = mapper(UserSessionMapper.class, method -> {
            if ("insert".equals(method)) {
                return args -> {
                    UserSessionPo session = (UserSessionPo) args[0];
                    session.setId(200L);
                    sessions.add(session);
                    return 1;
                };
            }
            if ("selectOne".equals(method)) {
                return args -> sessions.stream()
                        .filter(session -> !Boolean.TRUE.equals(session.getRevoked()))
                        .findFirst()
                        .orElse(null);
            }
            return null;
        });
        return new AuthServiceImpl(userMapper, sessionMapper, passwordHasher, new TokenService(passwordHasher), 7);
    }

    private UserPo activeUser(String passwordHash) {
        UserPo user = new UserPo();
        user.setId(100L);
        user.setUsername("admin");
        user.setDisplayName("管理员");
        user.setPasswordHash(passwordHash);
        user.setRole(UserRole.ADMIN.name());
        user.setStatus(UserStatus.ACTIVE.name());
        return user;
    }

    private static <T> T mapper(Class<T> type, Function<String, Invocation> behavior) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    Invocation invocation = behavior.apply(method.getName());
                    if (invocation != null) {
                        return invocation.invoke(args == null ? new Object[0] : args);
                    }
                    if (method.getReturnType().equals(int.class) || method.getReturnType().equals(Integer.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class)) {
                        return false;
                    }
                    return null;
                }));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Object[] args);
    }
}
