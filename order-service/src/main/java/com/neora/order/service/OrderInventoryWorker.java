package com.neora.order.service;

import com.neora.order.model.InventoryState;
import com.neora.order.model.Order;
import com.neora.order.model.OrderItem;
import com.neora.order.model.OrderStatus;
import com.neora.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;

@Component
public class OrderInventoryWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderInventoryWorker.class);
    private final OrderRepository orders;
    private final MongoTemplate mongo;
    private final RestClient catalog;
    private final RestClient cart;
    private final String internalKey;

    public OrderInventoryWorker(OrderRepository orders, MongoTemplate mongo, RestClient.Builder builder,
                                @Value("${services.catalog-url:http://localhost:8082}") String catalogUrl,
                                @Value("${services.cart-url:http://localhost:8083}") String cartUrl,
                                @Value("${app.internal-api-key:change-this-development-internal-key-32-chars}") String internalKey) {
        this.orders = orders;
        this.mongo = mongo;
        this.catalog = builder.clone().baseUrl(catalogUrl).build();
        this.cart = builder.clone().baseUrl(cartUrl).build();
        this.internalKey = internalKey;
    }

    @Scheduled(fixedDelayString = "${app.inventory.poll-ms:5000}")
    public void reconcile() {
        for (Order order : orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.PENDING)) {
            reserve(order);
        }
        for (Order order : orders.findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState.RESERVED)) {
            clearPurchasedItems(order);
        }
        for (Order order : orders.findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState.RELEASE_PENDING)) {
            if (order.inventoryClaimedUntil() == null || !order.inventoryClaimedUntil().isAfter(Instant.now())) {
                releaseStock(order);
            }
        }
    }

    private void reserve(Order order) {
        Instant now = Instant.now();
        Query claim = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(order.id()),
                Criteria.where("inventoryState").is(InventoryState.PENDING),
                new Criteria().orOperator(Criteria.where("inventoryClaimedUntil").is(null),
                        Criteria.where("inventoryClaimedUntil").lt(now))));
        Order claimed = mongo.findAndModify(claim,
                new Update().set("inventoryClaimedUntil", now.plusSeconds(30)), Order.class);
        if (claimed == null) return;
        try {
            catalog.post().uri("/internal/inventory/reservations/{orderId}", order.id())
                    .header("X-Internal-Api-Key", internalKey)
                    .body(new LinesRequest(order.items().stream().map(OrderInventoryWorker::line).toList()))
                    .retrieve().toBodilessEntity();
            var confirmed = mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                            .and("inventoryState").is(InventoryState.PENDING)),
                    new Update().set("inventoryState", InventoryState.RESERVED)
                            .set("status", OrderStatus.CONFIRMED).set("updatedAt", Instant.now())
                            .unset("inventoryClaimedUntil"), Order.class);
            if (confirmed.getModifiedCount() == 0) {
                orders.findById(order.id()).filter(current -> current.status() == OrderStatus.CANCELLED)
                        .ifPresent(this::releaseStock);
            }
        } catch (RestClientResponseException response) {
            if (response.getStatusCode().value() == 409 || response.getStatusCode().value() == 400
                    || response.getStatusCode().value() == 404) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                                .and("inventoryState").is(InventoryState.PENDING)),
                        new Update().set("inventoryState", InventoryState.REJECTED)
                                .set("status", OrderStatus.CANCELLED).set("updatedAt", Instant.now())
                                .unset("inventoryClaimedUntil"), Order.class);
            } else {
                retryLater(order.id(), response);
            }
        } catch (RestClientException response) {
            retryLater(order.id(), response);
        }
    }

    private void clearPurchasedItems(Order order) {
        try {
            List<String> ids = order.items().stream().map(OrderItem::cartItemId)
                    .filter(id -> id != null && !id.isBlank()).toList();
            cart.post().uri("/internal/cart/consume/{userId}", order.userId())
                    .header("X-Internal-Api-Key", internalKey).body(new CartConsumeRequest(ids))
                    .retrieve().toBodilessEntity();
            mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                            .and("inventoryState").is(InventoryState.RESERVED).and("cartClearedAt").is(null)),
                    new Update().set("cartClearedAt", Instant.now()), Order.class);
        } catch (RestClientException exception) {
            log.warn("Chưa thể dọn các món đã đặt khỏi giỏ cho đơn {}. Sẽ thử lại.", order.id(), exception);
        }
    }

    private void releaseStock(Order order) {
        try {
            catalog.post().uri("/internal/inventory/reservations/{orderId}/release", order.id())
                    .header("X-Internal-Api-Key", internalKey)
                    .body(new LinesRequest(order.items().stream().map(OrderInventoryWorker::line).toList()))
                    .retrieve().toBodilessEntity();
            mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                            .and("inventoryState").is(InventoryState.RELEASE_PENDING)),
                    new Update().set("inventoryState", InventoryState.RELEASED)
                            .set("updatedAt", Instant.now()).unset("inventoryClaimedUntil"), Order.class);
        } catch (RestClientException exception) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                            .and("status").is(OrderStatus.CANCELLED)
                            .and("inventoryState").is(InventoryState.RELEASED)),
                    new Update().set("inventoryState", InventoryState.RELEASE_PENDING), Order.class);
            log.warn("Chưa thể hoàn tồn kho cho đơn {}. Sẽ thử lại.", order.id(), exception);
        }
    }

    private void retryLater(String orderId, Exception exception) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(orderId).and("inventoryState").is(InventoryState.PENDING)),
                new Update().unset("inventoryClaimedUntil"), Order.class);
        log.warn("Chưa thể giữ hàng cho đơn {}. Sẽ thử lại.", orderId, exception);
    }

    private static InventoryLine line(OrderItem item) {
        return new InventoryLine(item.productId(), item.selectedSize(), item.selectedColor(), item.quantity());
    }

    private record LinesRequest(List<InventoryLine> lines) {}
    private record InventoryLine(String productId, String size, String color, int quantity) {}
    private record CartConsumeRequest(List<String> itemIds) {}
}
