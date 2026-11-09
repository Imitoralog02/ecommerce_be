package com.neora.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("notifications")
@CompoundIndex(name = "user_created_at", def = "{'userId': 1, 'createdAt': -1}")
public record Notification(@Id String id, String userId, String type, String title, String message,
                           String referenceId, boolean read, Instant createdAt) {
    public Notification markRead() {
        return new Notification(id, userId, type, title, message, referenceId, true, createdAt);
    }
}
