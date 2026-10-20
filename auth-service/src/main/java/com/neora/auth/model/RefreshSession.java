package com.neora.auth.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("refresh_sessions")
public record RefreshSession(
        @Id String id,
        String userId,
        Instant createdAt,
        @Indexed(expireAfter = "0s") Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt
) {}
