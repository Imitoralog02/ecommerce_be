package com.neora.order.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderQuoteRequest(@NotBlank String shippingType, String couponCode) {}
