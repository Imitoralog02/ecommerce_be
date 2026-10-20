package com.neora.order.repository;

import com.neora.order.model.Order;
import com.neora.order.model.InventoryState;
import com.neora.order.model.OrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Order> findByIdAndUserId(String id, String userId);
    List<Order> findTop100ByInventoryStateAndEventPublishedAtIsNullOrderByCreatedAtAsc(InventoryState state);
    List<Order> findTop100ByInventoryStateIsNullAndRequestFingerprintNotNullAndEventPublishedAtIsNullOrderByCreatedAtAsc();
    List<Order> findTop100ByInventoryStateOrderByCreatedAtAsc(InventoryState state);
    List<Order> findTop100ByInventoryStateAndCartClearedAtIsNullOrderByCreatedAtAsc(InventoryState state);
    List<Order> findTop100ByStatusAndRequestFingerprintNotNullAndCancelEventPublishedAtIsNullOrderByUpdatedAtAsc(OrderStatus status);
}
