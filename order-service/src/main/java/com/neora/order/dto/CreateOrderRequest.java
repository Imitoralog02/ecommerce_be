package com.neora.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String shippingType,
        @NotBlank String paymentType,
        @NotBlank String recipientName,
        @NotBlank @Pattern(regexp = "^[0-9+ .()-]{8,20}$") String phone,
        @NotBlank @Size(max = 300) String addressLine,
        String couponCode,
        List<@Valid ExpectedCartItem> expectedCartItems
) {
    public record ExpectedCartItem(@NotBlank String id, @Min(1) int quantity) {}
}
