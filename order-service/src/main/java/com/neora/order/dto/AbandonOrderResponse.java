package com.neora.order.dto;

public record AbandonOrderResponse(String outcome, OrderResponse order) {
    public static AbandonOrderResponse abandoned() {
        return new AbandonOrderResponse("ABANDONED", null);
    }

    public static AbandonOrderResponse found(OrderResponse order) {
        return new AbandonOrderResponse("ORDER_FOUND", order);
    }
}
