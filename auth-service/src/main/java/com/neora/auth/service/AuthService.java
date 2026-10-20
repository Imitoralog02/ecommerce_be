package com.neora.auth.service;

import com.neora.auth.dto.AuthResponse;
import com.neora.auth.dto.LoginRequest;
import com.neora.auth.dto.RegisterRequest;
import com.neora.auth.model.UserAccount;
import com.neora.auth.repository.UserAccountRepository;
import com.neora.shared.error.ConflictException;
import com.neora.shared.error.NotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserAccountRepository users, PasswordEncoder passwords, JwtService jwt,
                       RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("Email đã được sử dụng");
        Instant now = Instant.now();
        UserAccount user;
        try {
            user = users.save(new UserAccount(null, email, passwords.encode(request.password()),
                    request.fullName().trim(), normalize(request.phone()), "CUSTOMER", now, now));
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException("Email đã được sử dụng");
        }
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BadCredentialsException("invalid"));
        if (!passwords.matches(request.password(), user.passwordHash())) throw new BadCredentialsException("invalid");
        return response(user);
    }

    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokens.rotate(refreshToken);
        UserAccount user = find(rotated.userId());
        return response(user, rotated.token());
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    public AuthResponse.User profile(String userId) {
        return AuthResponse.User.from(find(userId));
    }

    public AuthResponse.User updateProfile(String userId, String fullName, String phone) {
        return AuthResponse.User.from(users.save(find(userId).withProfile(fullName.trim(), normalize(phone))));
    }

    private AuthResponse response(UserAccount user) {
        return response(user, refreshTokens.issue(user.id()));
    }

    private AuthResponse response(UserAccount user, RefreshTokenService.IssuedToken refreshToken) {
        return new AuthResponse(jwt.issue(user), "Bearer", jwt.expiresInSeconds(),
                refreshToken.value(), refreshToken.expiresIn(), AuthResponse.User.from(user));
    }

    private UserAccount find(String id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
