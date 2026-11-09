package com.neora.auth.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("password_reset_challenges")
public record PasswordResetChallenge(@Id String userId, String codeHash, Instant issuedAt,
                                     @Indexed(expireAfter = "0s") Instant expiresAt,
                                     int attempts, Instant consumedAt) {}
