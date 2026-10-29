package com.neora.order.service;

import com.neora.order.dto.CreateOrderRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class OrderIdempotency {
    private OrderIdempotency() {}

    public static String orderId(String userId, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,128}")) {
            throw new IllegalArgumentException("Idempotency-Key phải dài 8–128 ký tự và chỉ chứa chữ, số, _ hoặc -");
        }
        return "ord_" + digest(userId + "\u0000" + key);
    }

    public static String fingerprint(CreateOrderRequest request) {
        String original = String.join("\u0000",
                OrderPricing.shippingType(request.shippingType()),
                OrderPricing.paymentType(request.paymentType()),
                request.recipientName().trim(),
                request.phone().trim(),
                request.addressLine().trim(),
                request.couponCode() == null ? "" : request.couponCode().trim().toUpperCase());
        if (request.expectedCartItems() == null) return digest(original);
        String items = request.expectedCartItems().stream()
                .sorted(Comparator.comparing(CreateOrderRequest.ExpectedCartItem::id)
                        .thenComparingInt(CreateOrderRequest.ExpectedCartItem::quantity))
                .map(item -> item.id() + ":" + item.quantity())
                .collect(Collectors.joining("|"));
        return digest(original + "\u0000" + items);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng", exception);
        }
    }
}
