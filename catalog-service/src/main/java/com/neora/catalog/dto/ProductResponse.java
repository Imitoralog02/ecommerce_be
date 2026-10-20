package com.neora.catalog.dto;

import com.neora.catalog.model.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        String id, String pieceNumber, String title, String brand, String subCategory, String category,
        BigDecimal price, BigDecimal originalPrice, String composition, String description,
        List<String> availableSizes, List<String> availableColors, List<String> colorNames,
        String imageType, String imagePath, String tag, boolean isHeroPiece,
        double rating, int reviewCount, int soldCount, Instant createdAt, Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.pieceNumber(), product.title(), product.brand(),
                product.subCategory(), product.category(), product.price(), product.originalPrice(),
                product.composition(), product.description(), product.availableSizes(), product.availableColors(),
                product.colorNames(), product.imageType(), product.imagePath(), product.tag(), product.heroPiece(),
                product.rating(), product.reviewCount(), product.soldCount(), product.createdAt(), product.updatedAt());
    }
}
