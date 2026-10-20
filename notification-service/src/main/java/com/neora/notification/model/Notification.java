package com.neora.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("notifications")
public record Notification(@Id String id, @Indexed String userId, String type, String title, String message,
                           String referenceId, boolean read, Instant createdAt) {
    public Notification markRead() {
        return new Notification(id, userId, type, title, message, referenceId, true, createdAt);
    }
}
