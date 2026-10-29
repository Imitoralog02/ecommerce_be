package com.neora.order.service;

import com.neora.order.dto.CreateOrderRequest;
import com.neora.order.model.Order;
import com.neora.order.repository.OrderRepository;
import com.neora.shared.error.ConflictException;
import com.neora.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orders;

    @Test
    void repeatedRequestReturnsSavedOrderWithoutReadingCartAgain() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrderService service = new OrderService(orders, builder, "http://catalog-independent-cart");
        CreateOrderRequest request = request("CASH_ON_DELIVERY");
        String orderId = OrderIdempotency.orderId("user-1", "checkout-12345678");

        server.expect(requestTo("http://catalog-independent-cart/api/v1/cart"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"items":[{"id":"cart-1","product":{"id":"prod_dress_wrap","title":"Đầm hoa wrap","price":459000,"imagePath":"dress.jpg"},"selectedSize":"M","selectedColor":"Kem Be","quantity":1,"totalPrice":459000}],"subtotal":1,"discount":999999}
                        """, MediaType.APPLICATION_JSON));

        when(orders.findById(orderId)).thenReturn(Optional.empty());
        when(orders.insert(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var first = service.create("user-1", "jwt", "checkout-12345678", request);

        assertThat(first.total()).isEqualByComparingTo("489000");
        assertThat(first.discount()).isEqualByComparingTo("0");

        Order saved = new Order(first.id(), "user-1", first.status(), first.inventoryState(), first.items(), first.subtotal(),
                first.shippingFee(), first.discount(), first.total(), first.shippingType(), first.paymentType(),
                first.recipientName(), first.phone(), first.addressLine(), OrderIdempotency.fingerprint(request),
                null, null, null, null, first.createdAt(), first.updatedAt());
        when(orders.findById(orderId)).thenReturn(Optional.of(saved));
        var repeated = service.create("user-1", "jwt", "checkout-12345678", request);

        assertThat(repeated.id()).isEqualTo(first.id());
        verify(orders, times(1)).insert(any(Order.class));
        server.verify();
    }

    @Test
    void sameKeyWithDifferentAddressIsRejectedBeforeCallingCart() {
        RestClient.Builder builder = RestClient.builder();
        OrderService service = new OrderService(orders, builder, "http://cart");
        CreateOrderRequest original = request("CASH_ON_DELIVERY");
        String id = OrderIdempotency.orderId("user-1", "checkout-12345678");
        Order previous = new Order(id, "user-1", com.neora.order.model.OrderStatus.PENDING,
                com.neora.order.model.InventoryState.PENDING,
                java.util.List.of(), java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, "STANDARD", "CASH_ON_DELIVERY",
                "Nguyễn An", "0908123456", "12 Nguyễn Huệ", OrderIdempotency.fingerprint(original),
                null, null, null, null, java.time.Instant.now(), java.time.Instant.now());
        when(orders.findById(id)).thenReturn(Optional.of(previous));

        CreateOrderRequest changed = new CreateOrderRequest("EXPRESS", "CASH_ON_DELIVERY", "Nguyễn An",
                "0908123456", "Địa chỉ khác", null, original.expectedCartItems());
        assertThatThrownBy(() -> service.create("user-1", "jwt", "checkout-12345678", changed))
                .isInstanceOf(ConflictException.class);
        verify(orders, never()).insert(any(Order.class));
    }

    @Test
    void requestLookupUsesAuthenticatedUserAndNeverReadsCart() {
        OrderService service = new OrderService(orders, RestClient.builder(), "http://cart");
        String id = OrderIdempotency.orderId("user-1", "checkout-12345678");
        when(orders.findByIdAndUserId(id, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIdempotencyKey("user-1", "checkout-12345678"))
                .isInstanceOf(NotFoundException.class);
        verify(orders).findByIdAndUserId(id, "user-1");
        verifyNoMoreInteractions(orders);
    }

    private CreateOrderRequest request(String payment) {
        return new CreateOrderRequest("EXPRESS", payment, "Nguyễn An", "0908123456", "12 Nguyễn Huệ", null,
                java.util.List.of(new CreateOrderRequest.ExpectedCartItem("cart-1", 1)));
    }

    @Test
    void changedCartIsRejectedBeforeCreatingOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrderService service = new OrderService(orders, builder, "http://cart");
        String id = OrderIdempotency.orderId("user-1", "checkout-12345678");
        when(orders.findById(id)).thenReturn(Optional.empty());
        server.expect(requestTo("http://cart/api/v1/cart"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"cart-2","product":{"id":"prod_dress_wrap","title":"Đầm hoa wrap","price":459000,"imagePath":"dress.jpg"},"selectedSize":"M","selectedColor":"Kem Be","quantity":1,"totalPrice":459000}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.create("user-1", "jwt", "checkout-12345678", request("CASH_ON_DELIVERY")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Giỏ hàng đã thay đổi");
        verify(orders, never()).insert(any(Order.class));
        server.verify();
    }

    @Test
    void legacyRequestKeepsItsOriginalFingerprint() {
        CreateOrderRequest oldRequest = new CreateOrderRequest("EXPRESS", "CASH_ON_DELIVERY",
                "Nguyễn An", "0908123456", "12 Nguyễn Huệ", null, null);
        assertThat(OrderIdempotency.fingerprint(oldRequest))
                .isEqualTo("477eea19e4a648a0bf85cbde0c3408dbdaed09bd67b51389855269f367b6761a");
    }

    @Test
    void abandonedKeyBlocksLateOrderCreationAndStaysOutOfHistory() {
        OrderService service = new OrderService(orders, RestClient.builder(), "http://cart");
        String key = "checkout-12345678";
        String id = OrderIdempotency.orderId("user-1", key);
        when(orders.findById(id)).thenReturn(Optional.empty());
        when(orders.insert(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.abandon("user-1", key);
        assertThat(result.outcome()).isEqualTo("ABANDONED");
        assertThat(result.order()).isNull();
        var marker = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orders).insert(marker.capture());
        assertThat(marker.getValue().status()).isEqualTo(com.neora.order.model.OrderStatus.ABANDONED);
        when(orders.findById(id)).thenReturn(Optional.of(marker.getValue()));
        assertThatThrownBy(() -> service.create("user-1", "jwt", key, request("CASH_ON_DELIVERY")))
                .isInstanceOf(ConflictException.class);
        when(orders.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(java.util.List.of(marker.getValue()));
        assertThat(service.list("user-1")).isEmpty();
        when(orders.findByIdAndUserId(id, "user-1")).thenReturn(Optional.of(marker.getValue()));
        assertThatThrownBy(() -> service.getByIdempotencyKey("user-1", key))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void abandonReturnsExistingOrderInsteadOfCreatingMarker() {
        OrderService service = new OrderService(orders, RestClient.builder(), "http://cart");
        String key = "checkout-12345678";
        String id = OrderIdempotency.orderId("user-1", key);
        Order committed = existingOrder(id);
        when(orders.findById(id)).thenReturn(Optional.of(committed));

        var result = service.abandon("user-1", key);
        assertThat(result.outcome()).isEqualTo("ORDER_FOUND");
        assertThat(result.order().id()).isEqualTo(id);
        verify(orders, never()).insert(any(Order.class));
    }

    @Test
    void abandonResolvesRaceWhenOrderInsertWins() {
        OrderService service = new OrderService(orders, RestClient.builder(), "http://cart");
        String key = "checkout-12345678";
        String id = OrderIdempotency.orderId("user-1", key);
        when(orders.findById(id)).thenReturn(Optional.empty(), Optional.of(existingOrder(id)));
        when(orders.insert(any(Order.class))).thenThrow(new DuplicateKeyException("duplicate"));

        var result = service.abandon("user-1", key);
        assertThat(result.outcome()).isEqualTo("ORDER_FOUND");
        assertThat(result.order().id()).isEqualTo(id);
    }

    private Order existingOrder(String id) {
        var now = java.time.Instant.now();
        return new Order(id, "user-1", com.neora.order.model.OrderStatus.PENDING,
                com.neora.order.model.InventoryState.PENDING, java.util.List.of(), java.math.BigDecimal.ONE,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE,
                "STANDARD", "CASH_ON_DELIVERY", "Nguyễn An", "0908123456", "12 Nguyễn Huệ",
                OrderIdempotency.fingerprint(request("CASH_ON_DELIVERY")), null, null, null, null, now, now);
    }
}
