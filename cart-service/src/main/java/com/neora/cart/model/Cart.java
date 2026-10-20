package com.neora.cart.model;

import java.util.List;

public record Cart(String userId, List<CartItem> items) {
    public static Cart empty(String userId) { return new Cart(userId, List.of()); }
}
