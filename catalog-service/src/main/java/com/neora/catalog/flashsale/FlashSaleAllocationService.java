package com.neora.catalog.flashsale;

import com.neora.shared.error.ConflictException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class FlashSaleAllocationService {
    private static final int LEGACY_QUOTA = 20;
    private final MongoTemplate mongo;
    private final FlashSaleRepository campaigns;

    public FlashSaleAllocationService(MongoTemplate mongo, FlashSaleRepository campaigns) {
        this.mongo = mongo;
        this.campaigns = campaigns;
    }

    public FlashSaleAllocation allocation(FlashSale campaign, FlashSale.Offer offer) {
        int quota = offer.quota() == null ? LEGACY_QUOTA : offer.quota();
        if (quota < 1 || quota > 1_000_000) throw new IllegalArgumentException("Hạn mức Flash Sale không hợp lệ");
        String id = id(campaign.id(), offer.productId());
        return mongo.findAndModify(Query.query(Criteria.where("_id").is(id)),
                new Update().setOnInsert("campaignId", campaign.id())
                        .setOnInsert("productId", offer.productId())
                        .setOnInsert("capacity", quota).setOnInsert("available", quota)
                        .setOnInsert("reservations", java.util.Map.of()),
                FindAndModifyOptions.options().upsert(true).returnNew(true), FlashSaleAllocation.class);
    }

    public void reserve(String orderId, String campaignId, String productId, int quantity, java.math.BigDecimal price) {
        FlashSale campaign = campaigns.findById(campaignId)
                .orElseThrow(() -> new ConflictException("Chương trình Flash Sale không tồn tại"));
        FlashSale.Offer offer = campaign.offers() == null ? null : campaign.offers().stream()
                .filter(candidate -> productId.equals(candidate.productId())).findFirst().orElse(null);
        if (offer == null || price == null || offer.salePrice().compareTo(price) != 0) {
            throw new ConflictException("Giá Flash Sale của đơn hàng không khớp chương trình");
        }
        FlashSaleAllocation current = allocation(campaign, offer);
        String field = "reservations." + orderId;
        if (reserved(current, orderId, quantity)) return;
        if (current.reservations() != null && current.reservations().containsKey(orderId)) {
            throw new ConflictException("Đơn hàng đã giữ hạn mức Flash Sale với số lượng khác");
        }
        FlashSaleAllocation updated = mongo.findAndModify(Query.query(Criteria.where("_id").is(current.id())
                        .and("available").gte(quantity).and(field).exists(false)),
                new Update().inc("available", -quantity).set(field, quantity), FlashSaleAllocation.class);
        if (updated == null && !reserved(mongo.findById(current.id(), FlashSaleAllocation.class), orderId, quantity)) {
            throw new ConflictException("Sản phẩm đã hết lượt Flash Sale");
        }
    }

    public void release(String orderId, String campaignId, String productId, int quantity) {
        String field = "reservations." + orderId;
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id(campaignId, productId))
                        .and(field).is(quantity)),
                new Update().inc("available", quantity).unset(field), FlashSaleAllocation.class);
    }

    public void finalizeReservation(String orderId, String campaignId, String productId, int quantity) {
        String id = id(campaignId, productId);
        String field = "reservations." + orderId;
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and(field).is(quantity)),
                new Update().unset(field), FlashSaleAllocation.class);
        if (result.getModifiedCount() == 0) {
            FlashSaleAllocation current = mongo.findById(id, FlashSaleAllocation.class);
            if (current != null && current.reservations() != null && current.reservations().containsKey(orderId)) {
                throw new ConflictException("Số lượng Flash Sale đã giữ không khớp đơn hàng");
            }
        }
    }

    private static boolean reserved(FlashSaleAllocation allocation, String orderId, int quantity) {
        return allocation != null && allocation.reservations() != null
                && Integer.valueOf(quantity).equals(allocation.reservations().get(orderId));
    }

    private static String id(String campaignId, String productId) {
        return campaignId + ":" + productId;
    }
}
