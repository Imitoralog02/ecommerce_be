package com.neora.order.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document("orders")
@CompoundIndex(name = "user_created_at", def = "{'userId': 1, 'createdAt': -1}")
public record Order(
        @Id String id,
        String userId,
        OrderStatus status,
        InventoryState inventoryState,
        List<OrderItem> items,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal discount,
        BigDecimal total,
        String shippingType,
        String paymentType,
        String recipientName,
        String phone,
        String addressLine,
        String requestFingerprint,
        Instant inventoryClaimedUntil,
        Instant cartClearedAt,
        Instant eventPublishedAt,
        Instant cancelEventPublishedAt,
        Instant fulfillmentEventPublishedAt,
        String trackingNumber,
        String estimatedDelivery,
        Instant createdAt,
        Instant updatedAt,
        String couponCode
) {
    public Order(String id, String userId, OrderStatus status, InventoryState inventoryState,
                 List<OrderItem> items, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal discount,
                 BigDecimal total, String shippingType, String paymentType, String recipientName,
                 String phone, String addressLine, String requestFingerprint, Instant inventoryClaimedUntil,
                 Instant cartClearedAt, Instant eventPublishedAt, Instant cancelEventPublishedAt,
                 Instant fulfillmentEventPublishedAt, String trackingNumber, String estimatedDelivery,
                 Instant createdAt, Instant updatedAt) {
        this(id, userId, status, inventoryState, items, subtotal, shippingFee, discount, total,
                shippingType, paymentType, recipientName, phone, addressLine, requestFingerprint,
                inventoryClaimedUntil, cartClearedAt, eventPublishedAt, cancelEventPublishedAt,
                fulfillmentEventPublishedAt, trackingNumber, estimatedDelivery, createdAt, updatedAt, null);
    }
    public Order(String id, String userId, OrderStatus status, InventoryState inventoryState,
                 List<OrderItem> items, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal discount,
                 BigDecimal total, String shippingType, String paymentType, String recipientName,
                 String phone, String addressLine, String requestFingerprint, Instant inventoryClaimedUntil,
                 Instant cartClearedAt, Instant eventPublishedAt, Instant cancelEventPublishedAt,
                 Instant createdAt, Instant updatedAt) {
        this(id, userId, status, inventoryState, items, subtotal, shippingFee, discount, total,
                shippingType, paymentType, recipientName, phone, addressLine, requestFingerprint,
                inventoryClaimedUntil, cartClearedAt, eventPublishedAt, cancelEventPublishedAt,
                null, null, null, createdAt, updatedAt);
    }
}
