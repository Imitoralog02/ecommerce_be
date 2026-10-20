package com.neora.cart.model;

public record CartItem(String id, String productId, String selectedSize, String selectedColor, int quantity) {
    public CartItem withQuantity(int newQuantity) {
        return new CartItem(id, productId, selectedSize, selectedColor, newQuantity);
    }
}
