package com.neora.order.event;

import com.neora.events.OrderEvent;
import com.neora.events.OrderEventType;
import com.neora.order.model.Order;
import com.neora.order.model.InventoryState;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final KafkaTemplate<String, OrderEvent> kafka;
    private final OrderRepository orders;
    private final MongoTemplate mongo;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafka,
                               OrderRepository orders,
                               MongoTemplate mongo,
                               @Value("${app.kafka.order-topic:order.lifecycle.v1}") String topic) {
        this.kafka = kafka;
        this.orders = orders;
        this.mongo = mongo;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-ms:5000}")
    public void publishPending() {
        var ready = orders.findTop100ByInventoryStateAndEventPublishedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED);
        var legacy = orders.findTop100ByInventoryStateIsNullAndRequestFingerprintNotNullAndEventPublishedAtIsNullOrderByCreatedAtAsc();
        for (Order order : java.util.stream.Stream.concat(ready.stream(), legacy.stream()).toList()) {
            if (order.status() == null || !java.util.List.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
                    OrderStatus.SHIPPED, OrderStatus.DELIVERED).contains(order.status())) continue;
            try {
                OrderEvent event = new OrderEvent("ORDER_CREATED:" + order.id(), OrderEventType.ORDER_CREATED,
                        order.id(), order.userId(), OrderStatus.CONFIRMED.name(),
                        order.total(), order.createdAt());
                kafka.send(topic, order.id(), event).get(10, TimeUnit.SECONDS);
                mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                                .and("eventPublishedAt").is(null)),
                        new Update().set("eventPublishedAt", Instant.now()), Order.class);
            } catch (Exception exception) {
                log.warn("Không thể phát sự kiện cho đơn hàng {}. Sẽ thử lại.", order.id(), exception);
            }
        }
        for (Order order : orders.findTop100ByStatusAndRequestFingerprintNotNullAndCancelEventPublishedAtIsNullOrderByUpdatedAtAsc(OrderStatus.CANCELLED)) {
            try {
                OrderEvent event = new OrderEvent("ORDER_CANCELLED:" + order.id(), OrderEventType.ORDER_CANCELLED,
                        order.id(), order.userId(), order.status().name(), order.total(), order.updatedAt());
                kafka.send(topic, order.id(), event).get(10, TimeUnit.SECONDS);
                mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                                .and("cancelEventPublishedAt").is(null)),
                        new Update().set("cancelEventPublishedAt", Instant.now()), Order.class);
            } catch (Exception exception) {
                log.warn("Không thể phát sự kiện hủy đơn hàng {}. Sẽ thử lại.", order.id(), exception);
            }
        }
        for (OrderStatus status : new OrderStatus[]{OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED}) {
            Query pending = Query.query(Criteria.where("status").is(status)
                            .and("requestFingerprint").ne(null)
                            .and("fulfillmentEventPublishedAt").is(null))
                    .limit(100).with(org.springframework.data.domain.Sort.by("updatedAt"));
            for (Order order : mongo.find(pending, Order.class)) {
                try {
                    OrderEventType type = switch (status) {
                        case PROCESSING -> OrderEventType.ORDER_PROCESSING;
                        case SHIPPED -> OrderEventType.ORDER_SHIPPED;
                        case DELIVERED -> OrderEventType.ORDER_DELIVERED;
                        default -> throw new IllegalStateException("Unexpected fulfillment status");
                    };
                    OrderEvent event = new OrderEvent(type.name() + ":" + order.id(), type,
                            order.id(), order.userId(), status.name(), order.total(), order.updatedAt());
                    kafka.send(topic, order.id(), event).get(10, TimeUnit.SECONDS);
                    mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                                    .and("status").is(status).and("fulfillmentEventPublishedAt").is(null)),
                            new Update().set("fulfillmentEventPublishedAt", Instant.now()), Order.class);
                } catch (Exception exception) {
                    log.warn("Không thể phát sự kiện giao hàng cho đơn hàng {}. Sẽ thử lại.", order.id(), exception);
                }
            }
        }
    }
}
