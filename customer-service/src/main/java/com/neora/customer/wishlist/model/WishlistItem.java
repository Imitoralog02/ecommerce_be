package com.neora.customer.wishlist.model;

import java.time.Instant;

public record WishlistItem(String productId, Instant addedAt) {}
