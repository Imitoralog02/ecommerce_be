package com.neora.catalog.flashsale;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document("flash_sale_allocations")
public record FlashSaleAllocation(@Id String id, String campaignId, String productId,
                                  int capacity, int available, Map<String, Integer> reservations) {}
