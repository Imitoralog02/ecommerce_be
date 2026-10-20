package com.neora.order.model;

import java.math.BigDecimal;

public record OrderItem(
        String cartItemId,
        String productId,
        String title,
        String imagePath,
        String selectedSize,
        String selectedColor,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {}
