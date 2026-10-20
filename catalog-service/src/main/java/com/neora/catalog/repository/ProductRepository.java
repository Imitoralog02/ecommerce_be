package com.neora.catalog.repository;

import com.neora.catalog.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByActiveTrueOrderByCreatedAtDesc();
}
