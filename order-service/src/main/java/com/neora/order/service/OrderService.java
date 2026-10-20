package com.neora.order.service;

import com.neora.order.dto.CreateOrderRequest;
import com.neora.order.dto.OrderResponse;
import com.neora.order.dto.OrderQuoteRequest;
import com.neora.order.dto.OrderQuoteResponse;
import com.neora.order.model.Order;
import com.neora.order.model.OrderItem;
import com.neora.order.model.OrderStatus;
import com.neora.order.model.InventoryState;
import com.neora.order.repository.OrderRepository;
import com.neora.shared.error.NotFoundException;
import com.neora.shared.error.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final RestClient cart;

    public OrderService(OrderRepository orders, RestClient.Builder builder,
                        @Value("${services.cart-url:http://localhost:8083}") String cartUrl) {
        this.orders = orders;
        this.cart = builder.baseUrl(cartUrl).build();
    }

    public OrderQuoteResponse quote(String token, OrderQuoteRequest request) {
        return OrderPricing.quote(subtotal(loadCart(token)), request.shippingType(), request.couponCode());
    }

    public OrderResponse create(String userId, String token, String idempotencyKey, CreateOrderRequest request) {
        String orderId = OrderIdempotency.orderId(userId, idempotencyKey);
        String fingerprint = OrderIdempotency.fingerprint(request);
        Order existing = orders.findById(orderId).orElse(null);
        if (existing != null) return repeatedRequest(existing, userId, fingerprint);

        CartResponse cartResponse = loadCart(token);
        OrderQuoteResponse quote = OrderPricing.quote(subtotal(cartResponse), request.shippingType(), request.couponCode());
        List<OrderItem> items = cartResponse.items().stream().map(item -> new OrderItem(item.id(), item.product().id(),
                item.product().title(), item.product().imagePath(), item.selectedSize(), item.selectedColor(),
                item.quantity(), item.product().price(), item.totalPrice())).toList();
        Instant now = Instant.now();
        Order order;
        try {
            order = orders.insert(new Order(orderId, userId, OrderStatus.PENDING, InventoryState.PENDING,
                    items, quote.subtotal(),
                    quote.shippingFee(), quote.discount(), quote.total(),
                    OrderPricing.shippingType(request.shippingType()), OrderPricing.paymentType(request.paymentType()),
                    request.recipientName().trim(), request.phone().trim(), request.addressLine().trim(),
                    fingerprint, null, null, null, null, now, now));
        } catch (DuplicateKeyException duplicate) {
            Order saved = orders.findById(orderId).orElseThrow(() -> duplicate);
            return repeatedRequest(saved, userId, fingerprint);
        }
        return OrderResponse.from(order);
    }

    public List<OrderResponse> list(String userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream().map(OrderResponse::from).toList();
    }

    public OrderResponse get(String userId, String id) {
        return OrderResponse.from(orders.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng")));
    }

    private OrderResponse repeatedRequest(Order existing, String userId, String fingerprint) {
        if (!existing.userId().equals(userId) || !fingerprint.equals(existing.requestFingerprint())) {
            throw new ConflictException("Idempotency-Key đã được sử dụng cho một yêu cầu khác");
        }
        return OrderResponse.from(existing);
    }

    private CartResponse loadCart(String token) {
        CartResponse response = cart.get().uri("/api/v1/cart")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(CartResponse.class);
        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng đang trống");
        }
        return response;
    }

    private BigDecimal subtotal(CartResponse snapshot) {
        return snapshot.items().stream().map(item -> {
            if (item.product() == null || item.product().price() == null ||
                    item.product().price().signum() < 0 || item.quantity() < 1) {
                throw new IllegalArgumentException("Giỏ hàng chứa sản phẩm không hợp lệ");
            }
            return item.product().price().multiply(BigDecimal.valueOf(item.quantity()));
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record Product(String id, String title, BigDecimal price, String imagePath) {}
    private record CartItem(String id, Product product, String selectedSize, String selectedColor,
                            int quantity, BigDecimal totalPrice) {}
    private record CartResponse(List<CartItem> items, BigDecimal subtotal, BigDecimal discount) {}
}
