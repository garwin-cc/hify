package com.hify.auth.domain;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenService {

    private final PasswordHasher passwordHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(PasswordHasher passwordHasher) {
        this.passwordHasher = passwordHasher;
    }

    public String createToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        return passwordHasher.sha256(token);
    }
}
