package com.neora.order.dto;

import com.neora.order.model.Order;
import com.neora.order.model.OrderItem;
import com.neora.order.model.OrderStatus;
import com.neora.order.model.InventoryState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id, OrderStatus status, InventoryState inventoryState, List<OrderItem> items, BigDecimal subtotal, BigDecimal shippingFee,
        BigDecimal discount, BigDecimal total, String shippingType, String paymentType,
        String recipientName, String phone, String addressLine, Instant createdAt, Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.status(), order.inventoryState(), order.items(), order.subtotal(), order.shippingFee(),
                order.discount(), order.total(), order.shippingType(), order.paymentType(), order.recipientName(),
                order.phone(), order.addressLine(), order.createdAt(), order.updatedAt());
    }
}
