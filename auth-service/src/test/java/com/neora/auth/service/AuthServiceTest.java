package com.neora.auth.service;

import com.neora.auth.dto.AuthResponse;
import com.neora.auth.dto.LoginRequest;
import com.neora.auth.dto.RegisterRequest;
import com.neora.auth.model.UserAccount;
import com.neora.auth.repository.UserAccountRepository;
import com.neora.shared.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private UserAccountRepository users;
    private PasswordEncoder passwords;
    private JwtService jwt;
    private RefreshTokenService refreshTokens;
    private AuthService auth;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        passwords = mock(PasswordEncoder.class);
        jwt = mock(JwtService.class);
        refreshTokens = mock(RefreshTokenService.class);
        auth = new AuthService(users, passwords, jwt, refreshTokens);
        user = new UserAccount("user-1", "ana@example.com", "encoded", "Ana", null,
                "CUSTOMER", Instant.now(), Instant.now());
        when(jwt.issue(user)).thenReturn("access-token");
        when(jwt.expiresInSeconds()).thenReturn(1800L);
        when(refreshTokens.issue("user-1")).thenReturn(new RefreshTokenService.IssuedToken("refresh-token", 2592000));
    }

    @Test
    void loginReturnsBothTokens() {
        when(users.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("password123", "encoded")).thenReturn(true);

        AuthResponse response = auth.login(new LoginRequest("  Ana@Example.com  ", "password123"));

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(2592000, response.refreshExpiresIn());
        assertEquals("user-1", response.user().id());
    }

    @Test
    void refreshUsesRotatedToken() {
        when(refreshTokens.rotate("old-token")).thenReturn(new RefreshTokenService.RotatedToken("user-1",
                new RefreshTokenService.IssuedToken("new-token", 2592000)));
        when(users.findById("user-1")).thenReturn(Optional.of(user));

        AuthResponse response = auth.refresh("old-token");

        assertEquals("new-token", response.refreshToken());
        assertEquals("access-token", response.accessToken());
        verify(refreshTokens, never()).issue(anyString());
    }

    @Test
    void logoutRevokesRefreshToken() {
        auth.logout("refresh-token");
        verify(refreshTokens).revoke("refresh-token");
    }

    @Test
    void registrationRaceIsReportedAsConflict() {
        when(passwords.encode("password123")).thenReturn("encoded");
        when(users.save(any(UserAccount.class))).thenThrow(new DuplicateKeyException("duplicate email"));

        assertThrows(ConflictException.class, () -> auth.register(
                new RegisterRequest("Ana@Example.com", "password123", "Ana", null)));
        verify(refreshTokens, never()).issue(anyString());
    }
}
