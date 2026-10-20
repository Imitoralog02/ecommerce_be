package com.neora.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddItemRequest(
        @NotBlank String productId,
        @NotBlank String selectedSize,
        @NotBlank String selectedColor,
        @Min(1) @Max(99) int quantity
) {}
