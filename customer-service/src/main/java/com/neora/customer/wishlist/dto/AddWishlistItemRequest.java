package com.neora.customer.wishlist.dto;

import jakarta.validation.constraints.NotBlank;

public record AddWishlistItemRequest(@NotBlank String productId) {}
