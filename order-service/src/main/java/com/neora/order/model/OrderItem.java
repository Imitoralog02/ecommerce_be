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
        BigDecimal subtotal,
        String flashSaleId
) {
    public OrderItem(String cartItemId, String productId, String title, String imagePath,
                     String selectedSize, String selectedColor, int quantity,
                     BigDecimal unitPrice, BigDecimal subtotal) {
        this(cartItemId, productId, title, imagePath, selectedSize, selectedColor,
                quantity, unitPrice, subtotal, null);
    }
}
