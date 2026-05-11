package com.hify.auth.api;

public interface AuthService {

    LoginResp login(LoginReq req);

    void logout(String token);

    CurrentUser authenticate(String token);

    CurrentUser getCurrentUser();

    UserResp me();

    void changePassword(ChangePasswordReq req);
}
