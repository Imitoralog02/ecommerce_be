package com.neora.catalog.flashsale;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document("flash_sales")
public record FlashSale(@Id String id, String title, Instant startsAt, Instant endsAt,
                        boolean active, List<Offer> offers) {
    public record Offer(String productId, BigDecimal salePrice, Integer quota) {
        public Offer(String productId, BigDecimal salePrice) {
            this(productId, salePrice, null);
        }
    }
}
