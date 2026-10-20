package com.neora.catalog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document("products")
public record Product(
        @Id String id,
        @Indexed(unique = true) String pieceNumber,
        String title,
        String brand,
        String subCategory,
        @Indexed String category,
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
        boolean heroPiece,
        double rating,
        int reviewCount,
        int soldCount,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
