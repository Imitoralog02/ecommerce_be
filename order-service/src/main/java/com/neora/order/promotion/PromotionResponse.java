package com.neora.order.promotion;

import java.math.BigDecimal;
import java.time.Instant;

public record PromotionResponse(String code, String title, Promotion.DiscountType type,
                                BigDecimal value, BigDecimal minSubtotal, BigDecimal maxDiscount,
                                Instant endsAt, boolean available, Integer remainingUses) {}
