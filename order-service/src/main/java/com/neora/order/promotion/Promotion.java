package com.neora.order.promotion;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Document("promotions")
public record Promotion(
        @Id String code,
        String title,
        DiscountType type,
        BigDecimal value,
        BigDecimal minSubtotal,
        BigDecimal maxDiscount,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        Integer maxTotalUses,
        Integer maxUsesPerUser,
        int totalUses,
        Map<String, Integer> usedByUser,
        Map<String, Claim> claims
) {
    public enum DiscountType { PERCENT, FIXED, SHIPPING }

    public record Claim(String userId, Instant claimedAt) {}

    public Promotion(String code, String title, DiscountType type, BigDecimal value,
                     BigDecimal minSubtotal, BigDecimal maxDiscount, Instant startsAt,
                     Instant endsAt, boolean active) {
        this(code, title, type, value, minSubtotal, maxDiscount, startsAt, endsAt,
                active, null, null, 0, Map.of(), Map.of());
    }
}
