package com.neora.notification.controller;

import com.neora.notification.model.Notification;
import com.neora.notification.service.NotificationService;
import com.neora.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping public List<Notification> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return notifications.list(user.id());
    }
    @PatchMapping("/{id}/read") public Notification read(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @PathVariable String id) {
        return notifications.markRead(user.id(), id);
    }
}
