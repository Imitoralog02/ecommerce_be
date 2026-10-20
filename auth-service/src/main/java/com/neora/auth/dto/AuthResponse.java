package com.neora.auth.dto;

import com.neora.auth.model.UserAccount;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, long expiresIn,
                           String refreshToken, long refreshExpiresIn, User user) {
    public record User(String id, String email, String fullName, String phone, String role, Instant createdAt) {
        public static User from(UserAccount account) {
            return new User(account.id(), account.email(), account.fullName(), account.phone(), account.role(), account.createdAt());
        }
    }
}
