package com.neora.auth.controller;

import com.neora.auth.dto.AuthResponse;
import com.neora.auth.dto.LoginRequest;
import com.neora.auth.dto.RegisterRequest;
import com.neora.auth.service.AuthService;
import com.neora.auth.service.PasswordResetService;
import com.neora.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;
    private final PasswordResetService passwordResets;

    public AuthController(AuthService auth, PasswordResetService passwordResets) {
        this.auth = auth;
        this.passwordResets = passwordResets;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }

    @PostMapping("/auth/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) { auth.logout(request.refreshToken()); }

    @PostMapping("/auth/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResets.request(request.email());
    }

    @PostMapping("/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm request) {
        passwordResets.confirm(request.email(), request.code(), request.newPassword());
    }

    @GetMapping("/me")
    public AuthResponse.User me(@AuthenticationPrincipal AuthenticatedUser user) { return auth.profile(user.id()); }

    @PutMapping("/me")
    public AuthResponse.User update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile(user.id(), request.fullName(), request.phone());
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 100) String fullName, @Size(max = 20) String phone) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record PasswordResetRequest(@Email @NotBlank String email) {}
    public record PasswordResetConfirm(@Email @NotBlank String email,
                                       @NotBlank @Pattern(regexp = "[0-9]{8}") String code,
                                       @NotBlank @Size(min = 8, max = 72)
                                       @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).*")
                                       String newPassword) {}
}
