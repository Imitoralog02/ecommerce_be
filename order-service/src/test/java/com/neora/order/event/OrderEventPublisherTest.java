package com.neora.order.event;

import com.neora.events.OrderEvent;
import com.neora.order.model.Order;
import com.neora.order.model.OrderStatus;
import com.neora.order.model.InventoryState;
import com.neora.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {
    @Mock KafkaTemplate<String, OrderEvent> kafka;
    @Mock OrderRepository orders;
    @Mock MongoTemplate mongo;

    @Test
    void pendingOrderPublishesStableEventIdAndMarksItDelivered() {
        Instant createdAt = Instant.parse("2026-10-21T00:00:00Z");
        Order order = new Order("ord_123", "user-1", OrderStatus.CONFIRMED, InventoryState.RESERVED, List.of(),
                BigDecimal.valueOf(459000), BigDecimal.valueOf(30000), BigDecimal.ZERO,
                BigDecimal.valueOf(489000), "EXPRESS", "CASH_ON_DELIVERY", "Nguyễn An",
                "0908123456", "12 Nguyễn Huệ", "fingerprint", null, null, null, null, createdAt, createdAt);
        when(orders.findTop100ByInventoryStateAndEventPublishedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of(order));
        when(kafka.send(anyString(), anyString(), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new OrderEventPublisher(kafka, orders, mongo, "order.lifecycle.v1").publishPending();

        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafka).send(eq("order.lifecycle.v1"), eq("ord_123"), event.capture());
        assertThat(event.getValue().eventId()).isEqualTo("ORDER_CREATED:ord_123");
        assertThat(event.getValue().status()).isEqualTo("CONFIRMED");
        verify(mongo).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(Order.class));
    }

    @Test
    void failedSendLeavesOrderPendingForNextPoll() {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        Order order = new Order("ord_123", "user-1", OrderStatus.CONFIRMED, InventoryState.RESERVED, List.of(),
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                "STANDARD", "CASH_ON_DELIVERY", "Nguyễn An", "0908123456", "12 Nguyễn Huệ",
                "fingerprint", null, null, null, null, now, now);
        when(orders.findTop100ByInventoryStateAndEventPublishedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of(order));
        when(kafka.send(anyString(), anyString(), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        new OrderEventPublisher(kafka, orders, mongo, "order.lifecycle.v1").publishPending();

        verify(mongo, never()).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(Order.class));
    }

    @Test
    void processingOrderPublishesFulfillmentEvent() {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        Order order = new Order("ord_123", "user-1", OrderStatus.PROCESSING, InventoryState.RESERVED, List.of(),
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                "STANDARD", "CASH_ON_DELIVERY", "Nguyễn An", "0908123456", "12 Nguyễn Huệ",
                "fingerprint", null, null, null, null, now, now);
        when(mongo.find(any(org.springframework.data.mongodb.core.query.Query.class), eq(Order.class)))
                .thenReturn(List.of(order), List.of(), List.of());
        when(kafka.send(anyString(), anyString(), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new OrderEventPublisher(kafka, orders, mongo, "order.lifecycle.v1").publishPending();

        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafka).send(eq("order.lifecycle.v1"), eq("ord_123"), event.capture());
        assertThat(event.getValue().eventId()).isEqualTo("ORDER_PROCESSING:ord_123");
        verify(mongo).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(Order.class));
    }

    @Test
    void cancelledOrderPublishesStableCancellationEvent() {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        Order order = new Order("ord_123", "user-1", OrderStatus.CANCELLED, InventoryState.RELEASED, List.of(),
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                "STANDARD", "CASH_ON_DELIVERY", "Nguyễn An", "0908123456", "12 Nguyễn Huệ",
                "fingerprint", null, null, null, null, now, now);
        when(orders.findTop100ByStatusAndRequestFingerprintNotNullAndCancelEventPublishedAtIsNullOrderByUpdatedAtAsc(OrderStatus.CANCELLED))
                .thenReturn(List.of(order));
        when(kafka.send(anyString(), anyString(), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new OrderEventPublisher(kafka, orders, mongo, "order.lifecycle.v1").publishPending();

        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafka).send(eq("order.lifecycle.v1"), eq("ord_123"), event.capture());
        assertThat(event.getValue().eventId()).isEqualTo("ORDER_CANCELLED:ord_123");
        assertThat(event.getValue().status()).isEqualTo("CANCELLED");
        verify(mongo).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(Order.class));
    }
}
