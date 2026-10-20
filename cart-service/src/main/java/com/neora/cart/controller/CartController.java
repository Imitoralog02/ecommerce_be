package com.neora.cart.controller;

import com.neora.cart.dto.AddItemRequest;
import com.neora.cart.dto.CartResponse;
import com.neora.cart.service.CartService;
import com.neora.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService carts;
    public CartController(CartService carts) { this.carts = carts; }
    @GetMapping public CartResponse get(@AuthenticationPrincipal AuthenticatedUser user) { return carts.get(user.id()); }
    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED)
    public CartResponse add(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AddItemRequest request) {
        return carts.add(user.id(), request);
    }
    @PutMapping("/items/{itemId}")
    public CartResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String itemId,
                               @Valid @RequestBody UpdateItemRequest request) {
        return carts.update(user.id(), itemId, request.quantity());
    }
    @DeleteMapping("/items/{itemId}")
    public CartResponse remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String itemId) {
        return carts.remove(user.id(), itemId);
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthenticatedUser user) { carts.clear(user.id()); }
    public record UpdateItemRequest(@Min(1) @Max(99) int quantity) {}
}
