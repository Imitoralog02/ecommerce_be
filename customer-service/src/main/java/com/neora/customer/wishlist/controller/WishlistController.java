package com.neora.customer.wishlist.controller;

import com.neora.customer.wishlist.dto.AddWishlistItemRequest;
import com.neora.customer.wishlist.dto.WishlistResponse;
import com.neora.customer.wishlist.service.WishlistService;
import com.neora.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {
    private final WishlistService wishlists;

    public WishlistController(WishlistService wishlists) {
        this.wishlists = wishlists;
    }

    @GetMapping
    public WishlistResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return wishlists.get(user.id());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public WishlistResponse add(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody AddWishlistItemRequest request) {
        return wishlists.add(user.id(), request.productId());
    }

    @DeleteMapping("/items/{productId}")
    public WishlistResponse remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String productId) {
        return wishlists.remove(user.id(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthenticatedUser user) {
        wishlists.clear(user.id());
    }
}
