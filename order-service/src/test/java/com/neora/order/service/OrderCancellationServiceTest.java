package com.neora.order.service;

import com.neora.order.model.InventoryState;
import com.neora.order.model.Order;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import com.neora.shared.error.ConflictException;
import com.neora.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCancellationServiceTest {
    @Test
    void confirmedOrderMovesToReleasePending() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(orders.findByIdAndUserId("order-1", "user-1"))
                .thenReturn(Optional.of(order(OrderStatus.CONFIRMED, InventoryState.RESERVED)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Order.class)))
                .thenReturn(order(OrderStatus.CANCELLED, InventoryState.RELEASE_PENDING));

        var response = new OrderCancellationService(orders, mongo).cancel("user-1", "order-1");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.inventoryState()).isEqualTo(InventoryState.RELEASE_PENDING);
    }

    @Test
    void alreadyCancelledRequestIsIdempotent() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(orders.findByIdAndUserId("order-1", "user-1"))
                .thenReturn(Optional.of(order(OrderStatus.CANCELLED, InventoryState.RELEASED)));

        new OrderCancellationService(orders, mongo).cancel("user-1", "order-1");

        verifyNoInteractions(mongo);
    }

    @Test
    void shippedOrderCannotBeCancelled() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(orders.findByIdAndUserId("order-1", "user-1"))
                .thenReturn(Optional.of(order(OrderStatus.SHIPPED, InventoryState.RESERVED)));

        assertThatThrownBy(() -> new OrderCancellationService(orders, mongo).cancel("user-1", "order-1"))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(mongo);
    }

    @Test
    void userCannotCancelAnotherUsersOrder() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(orders.findByIdAndUserId("order-1", "other-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new OrderCancellationService(orders, mongo).cancel("other-user", "order-1"))
                .isInstanceOf(NotFoundException.class);
    }

    private Order order(OrderStatus status, InventoryState inventoryState) {
        Instant now = Instant.now();
        return new Order("order-1", "user-1", status, inventoryState, List.of(), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, "STANDARD", "CASH_ON_DELIVERY",
                "Ana", "0908123456", "12 Nguyễn Huệ", "fingerprint", null, null, null, null, now, now);
    }
}
