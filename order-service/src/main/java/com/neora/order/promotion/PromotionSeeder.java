package com.neora.order.promotion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.neora.order.model.Order;
import com.neora.order.model.OrderStatus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class PromotionSeeder implements CommandLineRunner {
    private final PromotionRepository promotions;
    private final MongoTemplate mongo;
    private final boolean enabled;

    public PromotionSeeder(PromotionRepository promotions, MongoTemplate mongo,
                           @Value("${app.promotion.seed-demo:true}") boolean enabled) {
        this.promotions = promotions;
        this.mongo = mongo;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) return;
        insertIfAbsent(demo("NEORA10", "Giảm 10%", Promotion.DiscountType.PERCENT,
                BigDecimal.TEN, BigDecimal.valueOf(200_000), BigDecimal.valueOf(100_000),
                null, null, true));
        insertIfAbsent(demo("NEORA30K", "Giảm 30.000đ", Promotion.DiscountType.FIXED,
                BigDecimal.valueOf(30_000), BigDecimal.valueOf(299_000), null,
                null, null, true));
        insertIfAbsent(demo("FREESHIP", "Miễn phí giao hàng", Promotion.DiscountType.SHIPPING,
                BigDecimal.valueOf(30_000), BigDecimal.ZERO, null,
                null, null, true));
    }

    private Promotion demo(String code, String title, Promotion.DiscountType type,
                           BigDecimal value, BigDecimal minSubtotal, BigDecimal maxDiscount,
                           java.time.Instant startsAt, java.time.Instant endsAt, boolean active) {
        return new Promotion(code, title, type, value, minSubtotal, maxDiscount,
                startsAt, endsAt, active, 1_000, 1, 0, Map.of(), Map.of());
    }

    private void insertIfAbsent(Promotion promotion) {
        if (promotions.existsById(promotion.code())) {
            upgradeLegacyDemo(promotion);
            return;
        }
        try {
            promotions.insert(promotion);
        } catch (DuplicateKeyException ignored) {
            // Another instance inserted the same demo promotion.
        }
    }

    private void upgradeLegacyDemo(Promotion demo) {
        Promotion current = promotions.findById(demo.code()).orElse(null);
        if (current == null || current.maxUsesPerUser() != null || current.maxTotalUses() != null
                || !current.title().equals(demo.title()) || current.type() != demo.type()
                || current.value().compareTo(demo.value()) != 0) return;
        Map<String, Promotion.Claim> claims = new HashMap<>();
        Map<String, Integer> byUser = new HashMap<>();
        for (Order order : mongo.find(Query.query(Criteria.where("couponCode").is(demo.code())
                .and("status").nin(OrderStatus.CANCELLED, OrderStatus.ABANDONED)), Order.class)) {
            claims.put(order.id(), new Promotion.Claim(order.userId(), order.createdAt()));
            byUser.merge(order.userId(), 1, Integer::sum);
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(demo.code())
                        .and("maxUsesPerUser").is(null).and("maxTotalUses").is(null)),
                new Update().set("maxTotalUses", demo.maxTotalUses())
                        .set("maxUsesPerUser", demo.maxUsesPerUser())
                        .set("totalUses", claims.size()).set("usedByUser", byUser)
                        .set("claims", claims), Promotion.class);
    }
}
