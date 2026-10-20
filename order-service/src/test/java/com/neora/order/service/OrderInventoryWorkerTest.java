package com.neora.order.service;

import com.neora.order.model.InventoryState;
import com.neora.order.model.Order;
import com.neora.order.model.OrderItem;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OrderInventoryWorkerTest {
    private static final String ID = "ord_" + "a".repeat(64);

    @Test
    void reservesStockBeforeConfirmingOrder() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Order pending = order(InventoryState.PENDING, OrderStatus.PENDING);
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.PENDING)).thenReturn(List.of(pending));
        when(orders.findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of());
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(pending);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Order.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        server.expect(requestTo("http://catalog/internal/inventory/reservations/" + ID))
                .andExpect(method(HttpMethod.POST)).andRespond(withNoContent());

        new OrderInventoryWorker(orders, mongo, builder, "http://catalog", "http://cart", "secret".repeat(6))
                .reconcile();

        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Order.class));
        assertThat(update.getValue().getUpdateObject().toString()).contains("CONFIRMED", "RESERVED");
        server.verify();
    }

    @Test
    void outOfStockCancelsOrder() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Order pending = order(InventoryState.PENDING, OrderStatus.PENDING);
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.PENDING)).thenReturn(List.of(pending));
        when(orders.findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of());
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(pending);
        server.expect(requestTo("http://catalog/internal/inventory/reservations/" + ID))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        new OrderInventoryWorker(orders, mongo, builder, "http://catalog", "http://cart", "secret".repeat(6))
                .reconcile();

        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Order.class));
        assertThat(update.getValue().getUpdateObject().toString()).contains("CANCELLED", "REJECTED");
        server.verify();
    }

    @Test
    void catalogOutageLeavesOrderPendingForRetry() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Order pending = order(InventoryState.PENDING, OrderStatus.PENDING);
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.PENDING)).thenReturn(List.of(pending));
        when(orders.findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of());
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(pending);
        server.expect(requestTo("http://catalog/internal/inventory/reservations/" + ID))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        new OrderInventoryWorker(orders, mongo, builder, "http://catalog", "http://cart", "secret".repeat(6))
                .reconcile();

        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Order.class));
        assertThat(update.getValue().getUpdateObject().toString()).contains("inventoryClaimedUntil")
                .doesNotContain("CANCELLED", "REJECTED", "CONFIRMED");
        server.verify();
    }

    @Test
    void cancelledOrderReleasesReservedStock() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.PENDING)).thenReturn(List.of());
        when(orders.findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of());
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.RELEASE_PENDING))
                .thenReturn(List.of(order(InventoryState.RELEASE_PENDING, OrderStatus.CANCELLED)));
        server.expect(requestTo("http://catalog/internal/inventory/reservations/" + ID + "/release"))
                .andExpect(method(HttpMethod.POST)).andRespond(withNoContent());

        new OrderInventoryWorker(orders, mongo, builder, "http://catalog", "http://cart", "secret".repeat(6))
                .reconcile();

        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Order.class));
        assertThat(update.getValue().getUpdateObject().toString()).contains("RELEASED");
        server.verify();
    }

    @Test
    void cancellationDuringReservationStillReleasesStock() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Order pending = order(InventoryState.PENDING, OrderStatus.PENDING);
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.PENDING)).thenReturn(List.of(pending));
        when(orders.findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of());
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(Order.class))).thenReturn(pending);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Order.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));
        when(orders.findById(ID)).thenReturn(Optional.of(order(InventoryState.RELEASE_PENDING, OrderStatus.CANCELLED)));
        server.expect(requestTo("http://catalog/internal/inventory/reservations/" + ID))
                .andRespond(withNoContent());
        server.expect(requestTo("http://catalog/internal/inventory/reservations/" + ID + "/release"))
                .andRespond(withNoContent());

        new OrderInventoryWorker(orders, mongo, builder, "http://catalog", "http://cart", "secret".repeat(6))
                .reconcile();

        verify(mongo, times(2)).updateFirst(any(Query.class), any(Update.class), eq(Order.class));
        server.verify();
    }

    @Test
    void releaseWaitsWhileReservationAttemptOwnsLease() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        Order base = order(InventoryState.RELEASE_PENDING, OrderStatus.CANCELLED);
        Order leased = new Order(base.id(), base.userId(), base.status(), base.inventoryState(), base.items(),
                base.subtotal(), base.shippingFee(), base.discount(), base.total(), base.shippingType(),
                base.paymentType(), base.recipientName(), base.phone(), base.addressLine(),
                base.requestFingerprint(), Instant.now().plusSeconds(30), base.cartClearedAt(),
                base.eventPublishedAt(), base.cancelEventPublishedAt(), base.createdAt(), base.updatedAt());
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.PENDING)).thenReturn(List.of());
        when(orders.findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of());
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.RELEASE_PENDING))
                .thenReturn(List.of(leased));

        new OrderInventoryWorker(orders, mongo, builder, "http://catalog", "http://cart", "secret".repeat(6))
                .reconcile();

        verifyNoInteractions(mongo);
    }

    @Test
    void confirmedOrderRemovesOnlyOriginalCartIds() {
        OrderRepository orders = mock(OrderRepository.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        when(orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.PENDING)).thenReturn(List.of());
        when(orders.findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED))
                .thenReturn(List.of(order(InventoryState.RESERVED, OrderStatus.CONFIRMED)));
        server.expect(requestTo("http://cart/internal/cart/consume/user-1"))
                .andExpect(method(HttpMethod.POST)).andRespond(withNoContent());

        new OrderInventoryWorker(orders, mongo, builder, "http://catalog", "http://cart", "secret".repeat(6))
                .reconcile();

        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Order.class));
        assertThat(update.getValue().getUpdateObject().toString()).contains("cartClearedAt");
        server.verify();
    }

    private Order order(InventoryState state, OrderStatus status) {
        Instant now = Instant.now();
        OrderItem item = new OrderItem("cart-1", "product-1", "Đầm", "dress.jpg", "M", "Đen",
                1, BigDecimal.valueOf(100), BigDecimal.valueOf(100));
        return new Order(ID, "user-1", status, state, List.of(item), BigDecimal.valueOf(100),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(100), "STANDARD", "CASH_ON_DELIVERY",
                "Ana", "0908123456", "12 Nguyễn Huệ", "fingerprint", null, null, null, null, now, now);
    }
}
