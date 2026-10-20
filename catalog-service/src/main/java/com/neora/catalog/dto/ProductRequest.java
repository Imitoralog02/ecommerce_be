package com.neora.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotBlank String pieceNumber,
        @NotBlank String title,
        @NotBlank String brand,
        @NotBlank String category,
        @NotNull @PositiveOrZero BigDecimal price,
        BigDecimal originalPrice,
        String description,
        List<String> availableSizes,
        List<String> availableColors,
        List<String> colorNames,
        String imagePath
) {}
