package com.neora.order.service;

import com.neora.order.dto.OrderResponse;
import com.neora.order.model.InventoryState;
import com.neora.order.model.Order;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import com.neora.order.promotion.PromotionService;
import com.neora.shared.error.ConflictException;
import com.neora.shared.error.NotFoundException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@Service
public class OrderCancellationService {
    private static final Logger log = LoggerFactory.getLogger(OrderCancellationService.class);
    private final OrderRepository orders;
    private final MongoTemplate mongo;
    private final PromotionService promotions;

    @Autowired
    public OrderCancellationService(OrderRepository orders, MongoTemplate mongo, PromotionService promotions) {
        this.orders = orders;
        this.mongo = mongo;
        this.promotions = promotions;
    }

    public OrderCancellationService(OrderRepository orders, MongoTemplate mongo) {
        this(orders, mongo, null);
    }

    public OrderResponse cancel(String userId, String orderId) {
        Order current = orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
        if (current.status() == OrderStatus.CANCELLED) return OrderResponse.from(current);
        if (current.status() == null || current.inventoryState() == null
                || !List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED).contains(current.status())
                || !List.of(InventoryState.PENDING, InventoryState.RESERVED).contains(current.inventoryState())) {
            throw new ConflictException("Không thể hủy đơn ở trạng thái hiện tại");
        }
        Query eligible = Query.query(Criteria.where("_id").is(orderId).and("userId").is(userId)
                .and("status").in(OrderStatus.PENDING, OrderStatus.CONFIRMED)
                .and("inventoryState").in(InventoryState.PENDING, InventoryState.RESERVED));
        Order cancelled = mongo.findAndModify(eligible,
                new Update().set("status", OrderStatus.CANCELLED)
                        .set("inventoryState", InventoryState.RELEASE_PENDING)
                        .set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Order.class);
        if (cancelled == null) throw new ConflictException("Trạng thái đơn hàng vừa thay đổi; vui lòng thử lại");
        if (promotions != null && cancelled.couponCode() != null) {
            try {
                promotions.release(cancelled.userId(), cancelled.id(), cancelled.couponCode());
            } catch (RuntimeException failure) {
                log.warn("Chưa thể hoàn lượt voucher cho đơn {}. Worker sẽ thử lại.", cancelled.id(), failure);
            }
        }
        return OrderResponse.from(cancelled);
    }
}
