package com.neora.cart.service;

import com.neora.cart.model.Cart;
import com.neora.cart.model.CartItem;
import com.neora.cart.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CartServiceTest {
    @Test
    void consumesOnlyPurchasedIdsAndIsIdempotent() {
        CartRepository carts = mock(CartRepository.class);
        CartService service = new CartService(carts, RestClient.builder(), "http://catalog");
        Cart original = new Cart("user-1", List.of(
                new CartItem("ordered", "product-1", "M", "Đen", 1),
                new CartItem("new", "product-2", "S", "Trắng", 1)));
        AtomicReference<Cart> state = new AtomicReference<>(original);
        when(carts.mutate(eq("user-1"), any())).thenAnswer(invocation -> {
            UnaryOperator<Cart> mutation = invocation.getArgument(1);
            return state.updateAndGet(mutation);
        });

        service.consumeItems("user-1", List.of("ordered"));
        service.consumeItems("user-1", List.of("ordered"));

        assertEquals(new Cart("user-1", List.of(original.items().get(1))), state.get());
        verify(carts, times(2)).mutate(eq("user-1"), any());
    }

    @Test
    void clearUsesSameAtomicMutationPath() {
        CartRepository carts = mock(CartRepository.class);
        CartService service = new CartService(carts, RestClient.builder(), "http://catalog");
        Cart original = new Cart("user-1", List.of(new CartItem("item", "product-1", "M", "Đen", 1)));
        AtomicReference<Cart> state = new AtomicReference<>(original);
        when(carts.mutate(eq("user-1"), any())).thenAnswer(invocation -> {
            UnaryOperator<Cart> mutation = invocation.getArgument(1);
            return state.updateAndGet(mutation);
        });

        service.clear("user-1");

        assertEquals(Cart.empty("user-1"), state.get());
        verify(carts).mutate(eq("user-1"), any());
    }
}
