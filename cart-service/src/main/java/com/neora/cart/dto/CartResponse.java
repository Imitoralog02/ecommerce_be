package com.neora.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<Item> items,
        int totalItemCount,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shippingFee,
        BigDecimal total
) {
    public record Product(String id, String title, BigDecimal price, List<String> availableSizes,
                          List<String> colorNames, String imagePath) {}
    public record Item(String id, Product product, String selectedSize, String selectedColor,
                       int quantity, BigDecimal totalPrice) {}
}
