package com.neora.catalog.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
public class InventoryController {
    private final InventoryService inventory;
    private final byte[] internalKey;

    public InventoryController(InventoryService inventory,
                               @Value("${app.internal-api-key:change-this-development-internal-key-32-chars}") String internalKey) {
        if (internalKey.length() < 32) throw new IllegalArgumentException("app.internal-api-key phải có ít nhất 32 ký tự");
        this.inventory = inventory;
        this.internalKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/api/v1/products/{productId}/availability")
    public InventoryService.Availability availability(@PathVariable String productId,
                                                      @RequestParam String size, @RequestParam String color) {
        return inventory.availability(productId, size, color);
    }

    @PostMapping("/internal/inventory/reservations/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
                        @PathVariable String orderId, @Valid @RequestBody LinesRequest request) {
        authorize(key);
        inventory.reserve(orderId, request.lines());
    }

    @PostMapping("/internal/inventory/reservations/{orderId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
                        @PathVariable String orderId, @Valid @RequestBody LinesRequest request) {
        authorize(key);
        inventory.release(orderId, request.lines());
    }

    private void authorize(String key) {
        if (key == null || !MessageDigest.isEqual(internalKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal API key");
        }
    }

    public record LinesRequest(@NotEmpty List<InventoryService.Line> lines) {}
}
