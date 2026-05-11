package com.hify.auth.api;

import com.hify.common.web.PageResult;

public interface UserService {

    PageResult<UserResp> list(int page, int size);

    UserResp create(CreateUserReq req);

    UserResp update(Long id, UpdateUserReq req);

    void resetPassword(Long id, ResetPasswordReq req);

    void delete(Long id);
}
