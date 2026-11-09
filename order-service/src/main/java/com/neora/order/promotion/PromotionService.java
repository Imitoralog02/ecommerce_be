package com.neora.order.promotion;

import com.neora.order.dto.OrderQuoteResponse;
import com.neora.order.service.OrderPricing;
import com.neora.order.model.Order;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import com.neora.shared.error.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Map;

@Service
public class PromotionService {
    private final PromotionRepository promotions;
    private final MongoTemplate mongo;
    private final OrderRepository orders;

    @Autowired
    public PromotionService(PromotionRepository promotions, MongoTemplate mongo, OrderRepository orders) {
        this.promotions = promotions;
        this.mongo = mongo;
        this.orders = orders;
    }

    public PromotionService(PromotionRepository promotions) {
        this(promotions, null, null);
    }

    public OrderQuoteResponse quote(BigDecimal subtotal, String shippingType, String couponCode) {
        return quote(null, subtotal, shippingType, couponCode);
    }

    public OrderQuoteResponse quote(String userId, BigDecimal subtotal, String shippingType, String couponCode) {
        OrderQuoteResponse base = OrderPricing.quote(subtotal, shippingType, null);
        if (couponCode == null || couponCode.isBlank()) return base;
        String code = normalize(couponCode);
        Promotion promotion = promotions.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Mã ưu đãi không tồn tại"));
        if (userId != null) ensureQuota(promotion, userId);
        return apply(base, promotion, Instant.now());
    }

    public List<PromotionResponse> list(String userId) {
        Instant now = Instant.now();
        return promotions.findAll().stream()
                .filter(promotion -> promotion.active()
                        && (promotion.startsAt() == null || !now.isBefore(promotion.startsAt()))
                        && (promotion.endsAt() == null || now.isBefore(promotion.endsAt())))
                .map(promotion -> new PromotionResponse(promotion.code(), promotion.title(), promotion.type(),
                        promotion.value(), promotion.minSubtotal(), promotion.maxDiscount(), promotion.endsAt(),
                        hasQuota(promotion, userId), remaining(promotion, userId)))
                .toList();
    }

    public boolean claim(String userId, String orderId, String couponCode) {
        if (couponCode == null || couponCode.isBlank()) return false;
        String code = normalize(couponCode);
        Promotion promotion = promotions.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Mã ưu đãi không tồn tại"));
        if (promotion.maxTotalUses() == null && promotion.maxUsesPerUser() == null) return false;
        String claimPath = "claims." + orderId;
        String userPath = "usedByUser." + userId;
        Instant now = Instant.now();
        Criteria condition = Criteria.where("_id").is(code).and("active").is(true)
                .and(claimPath).exists(false);
        List<Criteria> limits = new java.util.ArrayList<>();
        limits.add(new Criteria().orOperator(Criteria.where("startsAt").is(null),
                Criteria.where("startsAt").lte(now)));
        limits.add(new Criteria().orOperator(Criteria.where("endsAt").is(null),
                Criteria.where("endsAt").gt(now)));
        if (promotion.maxTotalUses() != null) {
            limits.add(new Criteria().orOperator(Criteria.where("totalUses").exists(false),
                    Criteria.where("totalUses").lt(promotion.maxTotalUses())));
        }
        if (promotion.maxUsesPerUser() != null) {
            limits.add(new Criteria().orOperator(Criteria.where(userPath).exists(false),
                    Criteria.where(userPath).lt(promotion.maxUsesPerUser())));
        }
        condition.andOperator(limits);
        Promotion claimed = mongo.findAndModify(Query.query(condition),
                new Update().set(claimPath, new Promotion.Claim(userId, now))
                        .inc("totalUses", 1).inc(userPath, 1),
                FindAndModifyOptions.options().returnNew(true), Promotion.class);
        if (claimed != null) return true;
        Promotion latest = promotions.findById(code).orElse(null);
        Promotion.Claim existing = latest == null || latest.claims() == null ? null : latest.claims().get(orderId);
        if (existing != null && existing.userId().equals(userId)) return false;
        throw new ConflictException("Mã ưu đãi đã hết lượt hoặc không còn hiệu lực");
    }

    public void release(String userId, String orderId, String couponCode) {
        if (couponCode == null || couponCode.isBlank()) return;
        String code = normalize(couponCode);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(code)
                        .and("claims." + orderId + ".userId").is(userId)),
                new Update().unset("claims." + orderId).inc("totalUses", -1)
                        .inc("usedByUser." + userId, -1), Promotion.class);
    }

    @Scheduled(fixedDelayString = "${app.promotion.reconcile-ms:30000}")
    public void reconcile() {
        Instant orphanCutoff = Instant.now().minusSeconds(300);
        for (Promotion promotion : promotions.findAll()) {
            if (promotion.claims() == null) continue;
            for (Map.Entry<String, Promotion.Claim> entry : promotion.claims().entrySet()) {
                Order order = orders.findById(entry.getKey()).orElse(null);
                if (order == null && entry.getValue().claimedAt() != null
                        && entry.getValue().claimedAt().isBefore(orphanCutoff)
                        || order != null && (order.status() == OrderStatus.CANCELLED
                        || order.status() == OrderStatus.ABANDONED)) {
                    release(entry.getValue().userId(), entry.getKey(), promotion.code());
                }
            }
        }
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static void ensureQuota(Promotion promotion, String userId) {
        if (!hasQuota(promotion, userId)) {
            throw new ConflictException("Mã ưu đãi đã hết lượt sử dụng cho tài khoản này");
        }
    }

    private static boolean hasQuota(Promotion promotion, String userId) {
        return (promotion.maxTotalUses() == null || promotion.totalUses() < promotion.maxTotalUses())
                && (promotion.maxUsesPerUser() == null || promotion.usedByUser() == null
                || promotion.usedByUser().getOrDefault(userId, 0) < promotion.maxUsesPerUser());
    }

    private static Integer remaining(Promotion promotion, String userId) {
        int used = promotion.usedByUser() == null ? 0 : promotion.usedByUser().getOrDefault(userId, 0);
        Integer userRemaining = promotion.maxUsesPerUser() == null ? null
                : Math.max(0, promotion.maxUsesPerUser() - used);
        Integer totalRemaining = promotion.maxTotalUses() == null ? null
                : Math.max(0, promotion.maxTotalUses() - promotion.totalUses());
        if (userRemaining == null) return totalRemaining;
        if (totalRemaining == null) return userRemaining;
        return Math.min(userRemaining, totalRemaining);
    }

    static OrderQuoteResponse apply(OrderQuoteResponse base, Promotion promotion, Instant now) {
        if (!promotion.active() || promotion.startsAt() != null && now.isBefore(promotion.startsAt())
                || promotion.endsAt() != null && !now.isBefore(promotion.endsAt())) {
            throw new IllegalArgumentException("Mã ưu đãi đã hết hạn hoặc chưa có hiệu lực");
        }
        if (promotion.minSubtotal() != null && base.subtotal().compareTo(promotion.minSubtotal()) < 0) {
            throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu của mã ưu đãi");
        }
        if (promotion.type() == null || promotion.value() == null || promotion.value().signum() <= 0) {
            throw new IllegalArgumentException("Cấu hình mã ưu đãi không hợp lệ");
        }
        BigDecimal discount = switch (promotion.type()) {
            case FIXED -> promotion.value().min(base.subtotal());
            case SHIPPING -> promotion.value().min(base.shippingFee());
            case PERCENT -> {
                if (promotion.value().compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException("Cấu hình mã ưu đãi không hợp lệ");
                }
                yield base.subtotal().multiply(promotion.value()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            }
        };
        if (promotion.maxDiscount() != null && promotion.maxDiscount().signum() >= 0) {
            discount = discount.min(promotion.maxDiscount());
        }
        discount = discount.min(base.subtotal().add(base.shippingFee()));
        if (discount.signum() <= 0) {
            throw new IllegalArgumentException("Mã ưu đãi không áp dụng cho phương thức giao hàng này");
        }
        return new OrderQuoteResponse(base.subtotal(), base.shippingFee(), discount,
                base.total().subtract(discount), base.currency());
    }
}
