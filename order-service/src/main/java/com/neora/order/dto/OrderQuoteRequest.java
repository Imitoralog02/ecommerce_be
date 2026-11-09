package com.neora.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderQuoteRequest(@NotBlank String shippingType, @Size(max = 32) String couponCode) {}
