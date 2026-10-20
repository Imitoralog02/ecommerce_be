package com.neora.customer.wishlist.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CatalogProductResponse(
        String id,
        String pieceNumber,
        String title,
        String brand,
        String subCategory,
        String category,
        BigDecimal price,
        BigDecimal originalPrice,
        String composition,
        String description,
        List<String> availableSizes,
        List<String> availableColors,
        List<String> colorNames,
        String imageType,
        String imagePath,
        String tag,
        boolean isHeroPiece,
        double rating,
        int reviewCount,
        int soldCount,
        Instant createdAt,
        Instant updatedAt
) {}
