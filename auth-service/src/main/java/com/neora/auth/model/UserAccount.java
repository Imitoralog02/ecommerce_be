package com.neora.auth.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("users")
public record UserAccount(
        @Id String id,
        @Indexed(unique = true) String email,
        String passwordHash,
        String fullName,
        String phone,
        String role,
        Instant createdAt,
        Instant updatedAt
) {
    public UserAccount withProfile(String name, String newPhone) {
        return new UserAccount(id, email, passwordHash, name, newPhone, role, createdAt, Instant.now());
    }
}
