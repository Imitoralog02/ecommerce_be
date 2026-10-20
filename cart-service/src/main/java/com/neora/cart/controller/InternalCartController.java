package com.neora.cart.controller;

import com.neora.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
public class InternalCartController {
    private final CartService carts;
    private final byte[] internalKey;

    public InternalCartController(CartService carts,
                                  @Value("${app.internal-api-key:change-this-development-internal-key-32-chars}") String internalKey) {
        if (internalKey.length() < 32) throw new IllegalArgumentException("app.internal-api-key phải có ít nhất 32 ký tự");
        this.carts = carts;
        this.internalKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/cart/consume/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
                        @PathVariable String userId, @Valid @RequestBody ConsumeRequest request) {
        if (key == null || !MessageDigest.isEqual(internalKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal API key");
        }
        carts.consumeItems(userId, request.itemIds());
    }

    public record ConsumeRequest(@NotNull List<String> itemIds) {}
}
