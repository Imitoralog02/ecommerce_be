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
                    titleFor(event.status()), messageFor(event.status()),
                    event.orderId(), false, event.occurredAt()));
        } catch (DuplicateKeyException duplicate) {
            // Kafka có thể phát lại event; giữ nguyên trạng thái đã đọc của thông báo cũ.
        }
    }

    private String titleFor(String status) {
        return switch (status) {
            case "PENDING" -> "Đã nhận đơn hàng";
            case "CONFIRMED" -> "Đơn hàng đã xác nhận";
            case "PROCESSING" -> "Đơn hàng đang được chuẩn bị";
            case "SHIPPED" -> "Đơn hàng đang giao";
            case "DELIVERED" -> "Đơn hàng đã giao";
            case "CANCELLED" -> "Đơn hàng đã hủy";
            default -> "Cập nhật đơn hàng";
        };
    }

    private String messageFor(String status) {
        return switch (status) {
            case "PENDING" -> "Đơn hàng đang chờ xác nhận. Chạm để xem chi tiết.";
            case "CONFIRMED" -> "Đơn hàng đã được xác nhận. Chạm để xem chi tiết.";
            case "PROCESSING" -> "Đơn hàng đang được chuẩn bị. Chạm để xem chi tiết.";
            case "SHIPPED" -> "Đơn hàng đang trên đường giao. Chạm để xem chi tiết.";
            case "DELIVERED" -> "Đơn hàng đã giao thành công. Chạm để xem chi tiết.";
            case "CANCELLED" -> "Đơn hàng đã hủy. Chạm để xem chi tiết.";
            default -> "Có cập nhật mới cho đơn hàng của bạn. Chạm để xem chi tiết.";
        };
    }

    public List<Notification> list(String userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Notification markRead(String userId, String id) {
        return notifications.save(notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông báo")).markRead());
    }
}
