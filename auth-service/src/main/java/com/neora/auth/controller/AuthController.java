package com.neora.auth.controller;

import com.neora.auth.dto.AuthResponse;
import com.neora.auth.dto.LoginRequest;
import com.neora.auth.dto.RegisterRequest;
import com.neora.auth.service.AuthService;
import com.neora.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

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

    @GetMapping("/me")
    public AuthResponse.User me(@AuthenticationPrincipal AuthenticatedUser user) { return auth.profile(user.id()); }

    @PutMapping("/me")
    public AuthResponse.User update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile(user.id(), request.fullName(), request.phone());
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 100) String fullName, @Size(max = 20) String phone) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
}
