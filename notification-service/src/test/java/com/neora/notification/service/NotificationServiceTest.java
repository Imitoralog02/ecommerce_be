package com.neora.notification.service;

import com.neora.events.OrderEvent;
import com.neora.events.OrderEventType;
import com.neora.notification.model.Notification;
import com.neora.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repository;

    @Test
    void replayedKafkaEventDoesNotOverwriteReadState() {
        NotificationService service = new NotificationService(repository);
        OrderEvent event = new OrderEvent("ORDER_CREATED:ord_123", OrderEventType.ORDER_CREATED,
                "ord_123", "user-1", "PENDING", BigDecimal.valueOf(489000), Instant.now());
        when(repository.insert(any(Notification.class))).thenThrow(new DuplicateKeyException("already handled"));

        service.createFrom(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(event.eventId());
        assertThat(captor.getValue().title()).isEqualTo("Đã nhận đơn hàng");
        assertThat(captor.getValue().message()).doesNotContain("PENDING", "ord_123");
        verify(repository, never()).save(any(Notification.class));
    }
}
