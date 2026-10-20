package com.neora.customer.address.repository;

import com.neora.customer.address.model.ShippingAddress;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ShippingAddressRepository extends MongoRepository<ShippingAddress, String> {
    List<ShippingAddress> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<ShippingAddress> findByIdAndUserId(String id, String userId);
    long countByUserId(String userId);
}
