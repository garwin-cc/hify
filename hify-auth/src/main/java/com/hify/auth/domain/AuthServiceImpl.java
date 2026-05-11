package com.hify.auth.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.auth.api.AuthService;
import com.hify.auth.api.ChangePasswordReq;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.LoginReq;
import com.hify.auth.api.LoginResp;
import com.hify.auth.api.UserResp;
import com.hify.auth.api.UserRole;
import com.hify.auth.api.UserStatus;
import com.hify.auth.infra.UserMapper;
import com.hify.auth.infra.UserSessionMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserSessionMapper sessionMapper;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    @Value("${hify.auth.session-days:7}")
    private int sessionDays;

    @Autowired
    public AuthServiceImpl(UserMapper userMapper, UserSessionMapper sessionMapper, PasswordHasher passwordHasher,
                           TokenService tokenService) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    AuthServiceImpl(UserMapper userMapper, UserSessionMapper sessionMapper, PasswordHasher passwordHasher,
                    TokenService tokenService, int sessionDays) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.sessionDays = sessionDays;
    }

    @Override
    @Transactional
    public LoginResp login(LoginReq req) {
        UserPo user = userMapper.selectOne(Wrappers.lambdaQuery(UserPo.class)
                .eq(UserPo::getUsername, req.getUsername().trim()));
        if (user == null || !UserStatus.ACTIVE.name().equals(user.getStatus())
                || !passwordHasher.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = tokenService.createToken();
        UserSessionPo session = new UserSessionPo();
        session.setUserId(user.getId());
        session.setTokenHash(tokenService.hashToken(token));
        session.setExpiresAt(LocalDateTime.now().plusDays(sessionDays));
        session.setRevoked(false);
        sessionMapper.insert(session);

        UserPo update = new UserPo();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);

        LoginResp resp = new LoginResp();
        resp.setToken(token);
        resp.setUser(toResp(user));
        return resp;
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionMapper.update(null, Wrappers.lambdaUpdate(UserSessionPo.class)
                .eq(UserSessionPo::getTokenHash, tokenService.hashToken(token))
                .set(UserSessionPo::getRevoked, true));
    }

    @Override
    public CurrentUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserSessionPo session = sessionMapper.selectOne(Wrappers.lambdaQuery(UserSessionPo.class)
                .eq(UserSessionPo::getTokenHash, tokenService.hashToken(token))
                .eq(UserSessionPo::getRevoked, false)
                .gt(UserSessionPo::getExpiresAt, LocalDateTime.now()));
        if (session == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserPo user = userMapper.selectById(session.getUserId());
        if (user == null || !UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return toCurrentUser(user);
    }

    @Override
    public CurrentUser getCurrentUser() {
        CurrentUser user = CurrentUserContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    @Override
    public UserResp me() {
        return toResp(userMapper.selectById(getCurrentUser().getId()));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordReq req) {
        CurrentUser currentUser = getCurrentUser();
        UserPo user = userMapper.selectById(currentUser.getId());
        if (user == null || !passwordHasher.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原密码错误");
        }
        UserPo update = new UserPo();
        update.setId(user.getId());
        update.setPasswordHash(passwordHasher.hash(req.getNewPassword()));
        userMapper.updateById(update);
        sessionMapper.update(null, Wrappers.lambdaUpdate(UserSessionPo.class)
                .eq(UserSessionPo::getUserId, user.getId())
                .set(UserSessionPo::getRevoked, true));
    }

    static UserResp toResp(UserPo po) {
        if (po == null) {
            return null;
        }
        UserResp resp = new UserResp();
        resp.setId(po.getId());
        resp.setUsername(po.getUsername());
        resp.setDisplayName(po.getDisplayName());
        resp.setRole(UserRole.valueOf(po.getRole()));
        resp.setStatus(UserStatus.valueOf(po.getStatus()));
        resp.setLastLoginAt(po.getLastLoginAt());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private CurrentUser toCurrentUser(UserPo po) {
        CurrentUser user = new CurrentUser();
        user.setId(po.getId());
        user.setUsername(po.getUsername());
        user.setDisplayName(po.getDisplayName());
        user.setRole(UserRole.valueOf(po.getRole()));
        return user;
    }
}
