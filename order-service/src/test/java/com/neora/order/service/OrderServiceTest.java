package com.neora.order.service;

import com.neora.order.dto.CreateOrderRequest;
import com.neora.order.model.Order;
import com.neora.order.repository.OrderRepository;
import com.neora.shared.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
                null, null, null, first.createdAt(), first.updatedAt());
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
                null, null, null, java.time.Instant.now(), java.time.Instant.now());
        when(orders.findById(id)).thenReturn(Optional.of(previous));

        CreateOrderRequest changed = new CreateOrderRequest("EXPRESS", "CASH_ON_DELIVERY", "Nguyễn An",
                "0908123456", "Địa chỉ khác", null);
        assertThatThrownBy(() -> service.create("user-1", "jwt", "checkout-12345678", changed))
                .isInstanceOf(ConflictException.class);
        verify(orders, never()).insert(any(Order.class));
    }

    private CreateOrderRequest request(String payment) {
        return new CreateOrderRequest("EXPRESS", payment, "Nguyễn An", "0908123456", "12 Nguyễn Huệ", null);
    }
}
