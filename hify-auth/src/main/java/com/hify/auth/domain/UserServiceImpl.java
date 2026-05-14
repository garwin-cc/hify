package com.hify.auth.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.auth.api.AuditLogRecord;
import com.hify.auth.api.AuditLogService;
import com.hify.auth.api.CreateUserReq;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.ResetPasswordReq;
import com.hify.auth.api.UpdateUserReq;
import com.hify.auth.api.UserResp;
import com.hify.auth.api.UserService;
import com.hify.auth.api.UserStatus;
import com.hify.auth.infra.UserMapper;
import com.hify.auth.infra.UserSessionMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.log.TraceContext;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserSessionMapper sessionMapper;
    private final PasswordHasher passwordHasher;
    private final AuditLogService auditLogService;

    @Override
    public PageResult<UserResp> list(int page, int size) {
        Page<UserPo> result = userMapper.selectPage(PageHelper.toPage(page, size),
                Wrappers.lambdaQuery(UserPo.class).orderByDesc(UserPo::getCreatedAt));
        return PageHelper.toPageResult(result, AuthServiceImpl::toResp);
    }

    @Override
    @Transactional
    public UserResp create(CreateUserReq req) {
        Long exists = userMapper.selectCount(Wrappers.lambdaQuery(UserPo.class)
                .eq(UserPo::getUsername, req.getUsername().trim()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT, "用户名已存在");
        }
        UserPo user = new UserPo();
        user.setUsername(req.getUsername().trim());
        user.setDisplayName(req.getDisplayName().trim());
        user.setPasswordHash(passwordHasher.hash(req.getPassword()));
        user.setRole(req.getRole().name());
        user.setStatus(UserStatus.ACTIVE.name());
        userMapper.insert(user);
        recordAudit("USER_CREATE", user, null, userAudit(user), true, null);
        return AuthServiceImpl.toResp(user);
    }

    @Override
    public UserResp update(Long id, UpdateUserReq req) {
        UserPo beforePo = userMapper.selectById(id);
        UserPo update = new UserPo();
        update.setId(id);
        update.setDisplayName(req.getDisplayName().trim());
        update.setRole(req.getRole().name());
        update.setStatus(req.getStatus().name());
        userMapper.updateById(update);
        UserPo afterPo = userMapper.selectById(id);
        recordAudit("USER_UPDATE", afterPo, userAudit(beforePo), userAudit(afterPo), true, null);
        return AuthServiceImpl.toResp(afterPo);
    }

    @Override
    public void resetPassword(Long id, ResetPasswordReq req) {
        UserPo user = userMapper.selectById(id);
        UserPo update = new UserPo();
        update.setId(id);
        update.setPasswordHash(passwordHasher.hash(req.getPassword()));
        userMapper.updateById(update);
        sessionMapper.update(null, Wrappers.lambdaUpdate(UserSessionPo.class)
                .eq(UserSessionPo::getUserId, id)
                .set(UserSessionPo::getRevoked, true));
        recordAudit("USER_RESET_PASSWORD", user, null, userAudit(user), true, null);
    }

    @Override
    public void delete(Long id) {
        UserPo user = userMapper.selectById(id);
        userMapper.deleteById(id);
        sessionMapper.update(null, Wrappers.lambdaUpdate(UserSessionPo.class)
                .eq(UserSessionPo::getUserId, id)
                .set(UserSessionPo::getRevoked, true));
        recordAudit("USER_DELETE", user, userAudit(user), Map.of(), true, null);
    }

    private Map<String, Object> userAudit(UserPo user) {
        if (user == null) {
            return Map.of();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", user.getId());
        value.put("username", user.getUsername());
        value.put("displayName", user.getDisplayName());
        value.put("role", user.getRole());
        value.put("status", user.getStatus());
        return value;
    }

    private void recordAudit(String action, UserPo targetUser, Map<String, Object> before,
                             Map<String, Object> after, boolean success, String errorMessage) {
        if (auditLogService == null) {
            return;
        }
        CurrentUser actor = CurrentUserContext.get();
        auditLogService.record(AuditLogRecord.builder()
                .traceId(TraceContext.ensureTraceId())
                .actorUserId(actor == null ? null : actor.getId())
                .actorUsername(actor == null ? "" : actor.getUsername())
                .action(action)
                .resourceType("USER")
                .resourceId(targetUser == null ? null : targetUser.getId())
                .resourceName(targetUser == null ? "" : targetUser.getUsername())
                .success(success)
                .errorMessage(errorMessage)
                .before(before)
                .after(after)
                .build());
    }
}
