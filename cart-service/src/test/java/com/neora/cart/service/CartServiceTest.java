package com.neora.cart.service;

import com.neora.cart.model.Cart;
import com.neora.cart.model.CartItem;
import com.neora.cart.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.mockito.Mockito.*;

class CartServiceTest {
    @Test
    void consumesOnlyPurchasedIdsAndIsIdempotent() {
        CartRepository carts = mock(CartRepository.class);
        CartService service = new CartService(carts, RestClient.builder(), "http://catalog");
        Cart original = new Cart("user-1", List.of(
                new CartItem("ordered", "product-1", "M", "Đen", 1),
                new CartItem("new", "product-2", "S", "Trắng", 1)));
        when(carts.findByUserId("user-1")).thenReturn(original,
                new Cart("user-1", List.of(original.items().get(1))));

        service.consumeItems("user-1", List.of("ordered"));
        service.consumeItems("user-1", List.of("ordered"));

        verify(carts, times(1)).save(new Cart("user-1", List.of(original.items().get(1))));
    }
}
