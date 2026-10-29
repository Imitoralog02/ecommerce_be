package com.neora.order.service;

import com.neora.order.dto.OrderResponse;
import com.neora.order.model.InventoryState;
import com.neora.order.model.Order;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import com.neora.shared.error.ConflictException;
import com.neora.shared.error.NotFoundException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class OrderFulfillmentService {
    private final OrderRepository orders;
    private final MongoTemplate mongo;

    public OrderFulfillmentService(OrderRepository orders, MongoTemplate mongo) {
        this.orders = orders;
        this.mongo = mongo;
    }

    public OrderResponse advance(String orderId, OrderStatus next, String trackingNumber, String estimatedDelivery) {
        Order current = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
        OrderStatus previous = switch (next) {
            case PROCESSING -> OrderStatus.CONFIRMED;
            case SHIPPED -> OrderStatus.PROCESSING;
            case DELIVERED -> OrderStatus.SHIPPED;
            default -> throw new IllegalArgumentException("Trạng thái giao hàng không hợp lệ");
        };
        if (current.status() == next) return OrderResponse.from(current);
        if (current.status() != previous || current.inventoryState() != InventoryState.RESERVED) {
            throw new ConflictException("Không thể chuyển trạng thái đơn hàng tại thời điểm này");
        }
        if (next == OrderStatus.SHIPPED && (trackingNumber == null || trackingNumber.isBlank())) {
            throw new IllegalArgumentException("Cần mã vận đơn khi bắt đầu giao hàng");
        }
        if (estimatedDelivery != null && !estimatedDelivery.isBlank()) {
            try {
                LocalDate.parse(estimatedDelivery);
            } catch (DateTimeParseException invalid) {
                throw new IllegalArgumentException("Ngày dự kiến giao phải theo định dạng YYYY-MM-DD", invalid);
            }
        }
        Update update = new Update().set("status", next).set("updatedAt", Instant.now())
                .unset("fulfillmentEventPublishedAt");
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            update.set("trackingNumber", trackingNumber.trim());
        }
        if (estimatedDelivery != null && !estimatedDelivery.isBlank()) {
            update.set("estimatedDelivery", estimatedDelivery);
        }
        Order changed = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(orderId).and("status").is(previous)
                        .and("inventoryState").is(InventoryState.RESERVED)),
                update, FindAndModifyOptions.options().returnNew(true), Order.class);
        if (changed == null) throw new ConflictException("Trạng thái đơn hàng vừa thay đổi; vui lòng thử lại");
        return OrderResponse.from(changed);
    }
}
