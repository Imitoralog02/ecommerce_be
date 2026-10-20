package com.neora.catalog.inventory;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document("inventory_variants")
public record InventoryVariant(@Id String id, String productId, String size, String color,
                               int available, Map<String, Integer> reservations) {}
