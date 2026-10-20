package com.neora.customer.wishlist.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document("wishlists")
public record Wishlist(@Id String userId, List<WishlistItem> items) {
    public static Wishlist empty(String userId) {
        return new Wishlist(userId, List.of());
    }
}
