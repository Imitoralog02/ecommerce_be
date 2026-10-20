package com.neora.order.service;

import com.neora.order.dto.OrderQuoteResponse;

import java.math.BigDecimal;
import java.util.Locale;

public final class OrderPricing {
    private OrderPricing() {}

    public static OrderQuoteResponse quote(BigDecimal subtotal, String shippingType, String couponCode) {
        if (subtotal == null || subtotal.signum() <= 0) {
            throw new IllegalArgumentException("Giỏ hàng đang trống");
        }
        if (couponCode != null && !couponCode.isBlank()) {
            throw new IllegalArgumentException("Mã ưu đãi chưa được hỗ trợ khi đặt hàng");
        }
        String shipping = shippingType.trim().toUpperCase(Locale.ROOT);
        BigDecimal shippingFee = switch (shipping) {
            case "EXPRESS" -> BigDecimal.valueOf(30_000);
            case "STANDARD", "STORE_PICKUP" -> BigDecimal.ZERO;
            default -> throw new IllegalArgumentException("Phương thức giao hàng không hợp lệ");
        };
        return new OrderQuoteResponse(subtotal, shippingFee, BigDecimal.ZERO,
                subtotal.add(shippingFee), "VND");
    }

    public static String shippingType(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Phương thức giao hàng không hợp lệ");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("EXPRESS") && !normalized.equals("STANDARD") && !normalized.equals("STORE_PICKUP")) {
            throw new IllegalArgumentException("Phương thức giao hàng không hợp lệ");
        }
        return normalized;
    }

    public static String paymentType(String value) {
        if (value == null || !value.trim().equalsIgnoreCase("CASH_ON_DELIVERY")) {
            throw new IllegalArgumentException("Hiện chỉ hỗ trợ thanh toán khi nhận hàng (COD)");
        }
        return "CASH_ON_DELIVERY";
    }
}
