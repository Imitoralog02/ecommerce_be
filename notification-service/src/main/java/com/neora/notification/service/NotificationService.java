package com.neora.notification.service;

import com.neora.events.OrderEvent;
import com.neora.notification.model.Notification;
import com.neora.notification.repository.NotificationRepository;
import com.neora.shared.error.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    public NotificationService(NotificationRepository notifications) { this.notifications = notifications; }

    public void createFrom(OrderEvent event) {
        try {
            notifications.insert(new Notification(event.eventId(), event.userId(), event.type().name(),
                    "Cập nhật đơn hàng", "Đơn hàng " + event.orderId() + " có trạng thái " + event.status() + ".",
                    event.orderId(), false, event.occurredAt()));
        } catch (DuplicateKeyException duplicate) {
            // Kafka có thể phát lại event; giữ nguyên trạng thái đã đọc của thông báo cũ.
        }
    }

    public List<Notification> list(String userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Notification markRead(String userId, String id) {
        return notifications.save(notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông báo")).markRead());
    }
}
