package com.neora.order.controller;

import com.neora.order.dto.CreateOrderRequest;
import com.neora.order.dto.OrderResponse;
import com.neora.order.dto.OrderQuoteRequest;
import com.neora.order.dto.OrderQuoteResponse;
import com.neora.order.service.OrderService;
import com.neora.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }
    @PostMapping("/quote")
    public OrderQuoteResponse quote(Authentication authentication, @Valid @RequestBody OrderQuoteRequest request) {
        return orders.quote(authentication.getCredentials().toString(), request);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal AuthenticatedUser user, Authentication authentication,
                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                @Valid @RequestBody CreateOrderRequest request) {
        return orders.create(user.id(), authentication.getCredentials().toString(), idempotencyKey, request);
    }
    @GetMapping public List<OrderResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return orders.list(user.id());
    }
    @GetMapping("/{id}") public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return orders.get(user.id(), id);
    }
}
