package com.neora.order.dto;

import java.math.BigDecimal;

public record OrderQuoteResponse(
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal discount,
        BigDecimal total,
        String currency
) {}
