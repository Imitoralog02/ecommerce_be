package com.neora.notification.listener;

import com.neora.events.OrderEvent;
import com.neora.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {
    private final NotificationService notifications;
    public OrderEventListener(NotificationService notifications) { this.notifications = notifications; }
    @KafkaListener(topics = "${app.kafka.order-topic:order.lifecycle.v1}")
    public void handle(OrderEvent event) { notifications.createFrom(event); }
}
