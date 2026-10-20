package com.neora.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration ttl) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("app.jwt.secret phải có ít nhất 32 ký tự");
        }
        ttl = ttl == null ? Duration.ofMinutes(30) : ttl;
    }
}
