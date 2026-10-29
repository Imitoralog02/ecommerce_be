package com.neora.order.service;

import com.neora.order.model.InventoryState;
import com.neora.order.model.Order;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import com.neora.shared.error.ConflictException;
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

class OrderFulfillmentServiceTest {
    @Test
    void confirmedOrderCanEnterProcessing() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(orders.findById("order-1")).thenReturn(Optional.of(order(OrderStatus.CONFIRMED, InventoryState.RESERVED)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Order.class)))
                .thenReturn(order(OrderStatus.PROCESSING, InventoryState.RESERVED));

        var response = new OrderFulfillmentService(orders, mongo)
                .advance("order-1", OrderStatus.PROCESSING, null, null);

        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void shippedOrderRequiresTrackingNumberAndCannotSkipProcessing() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(orders.findById("order-1")).thenReturn(Optional.of(order(OrderStatus.CONFIRMED, InventoryState.RESERVED)));
        var service = new OrderFulfillmentService(orders, mongo);

        assertThatThrownBy(() -> service.advance("order-1", OrderStatus.SHIPPED, "TRACK-1", null))
                .isInstanceOf(ConflictException.class);
        reset(orders);
        when(orders.findById("order-1")).thenReturn(Optional.of(order(OrderStatus.PROCESSING, InventoryState.RESERVED)));
        assertThatThrownBy(() -> service.advance("order-1", OrderStatus.SHIPPED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mongo);
    }

    private Order order(OrderStatus status, InventoryState inventoryState) {
        Instant now = Instant.now();
        return new Order("order-1", "user-1", status, inventoryState, List.of(), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, "STANDARD", "CASH_ON_DELIVERY",
                "Ana", "0908123456", "12 Nguyễn Huệ", "fingerprint", null, null, null, null, now, now);
    }
}
