package com.neora.order.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document("orders")
public record Order(
        @Id String id,
        @Indexed String userId,
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
        Instant createdAt,
        Instant updatedAt
) {}
