package com.neora.customer.wishlist.dto;

import java.time.Instant;
import java.util.List;

public record WishlistResponse(List<Item> items, int totalCount) {
    public WishlistResponse {
        items = List.copyOf(items);
    }

    public record Item(String productId, Instant addedAt, CatalogProductResponse product) {}
}
