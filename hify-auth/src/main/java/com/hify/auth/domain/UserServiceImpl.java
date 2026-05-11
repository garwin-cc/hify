package com.hify.auth.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.auth.api.CreateUserReq;
import com.hify.auth.api.ResetPasswordReq;
import com.hify.auth.api.UpdateUserReq;
import com.hify.auth.api.UserResp;
import com.hify.auth.api.UserService;
import com.hify.auth.api.UserStatus;
import com.hify.auth.infra.UserMapper;
import com.hify.auth.infra.UserSessionMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserSessionMapper sessionMapper;
    private final PasswordHasher passwordHasher;

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
        return AuthServiceImpl.toResp(user);
    }

    @Override
    public UserResp update(Long id, UpdateUserReq req) {
        UserPo update = new UserPo();
        update.setId(id);
        update.setDisplayName(req.getDisplayName().trim());
        update.setRole(req.getRole().name());
        update.setStatus(req.getStatus().name());
        userMapper.updateById(update);
        return AuthServiceImpl.toResp(userMapper.selectById(id));
    }

    @Override
    public void resetPassword(Long id, ResetPasswordReq req) {
        UserPo update = new UserPo();
        update.setId(id);
        update.setPasswordHash(passwordHasher.hash(req.getPassword()));
        userMapper.updateById(update);
        sessionMapper.update(null, Wrappers.lambdaUpdate(UserSessionPo.class)
                .eq(UserSessionPo::getUserId, id)
                .set(UserSessionPo::getRevoked, true));
    }

    @Override
    public void delete(Long id) {
        userMapper.deleteById(id);
        sessionMapper.update(null, Wrappers.lambdaUpdate(UserSessionPo.class)
                .eq(UserSessionPo::getUserId, id)
                .set(UserSessionPo::getRevoked, true));
    }
}
