package com.neora.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank String shippingType,
        @NotBlank String paymentType,
        @NotBlank String recipientName,
        @NotBlank @Pattern(regexp = "^[0-9+ .()-]{8,20}$") String phone,
        @NotBlank @Size(max = 300) String addressLine,
        String couponCode
) {}
