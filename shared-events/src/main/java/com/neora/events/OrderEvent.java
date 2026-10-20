package com.neora.events;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(
        String eventId,
        OrderEventType type,
        String orderId,
        String userId,
        String status,
        BigDecimal total,
        Instant occurredAt
) {}
