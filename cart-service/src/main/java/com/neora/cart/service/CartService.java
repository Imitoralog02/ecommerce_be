package com.neora.cart.service;

import com.neora.cart.dto.AddItemRequest;
import com.neora.cart.dto.CartResponse;
import com.neora.cart.model.Cart;
import com.neora.cart.model.CartItem;
import com.neora.cart.repository.CartRepository;
import com.neora.shared.error.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {
    private final CartRepository carts;
    private final RestClient catalog;

    public CartService(CartRepository carts, RestClient.Builder builder,
                       @Value("${services.catalog-url:http://localhost:8082}") String catalogUrl) {
        this.carts = carts;
        this.catalog = builder.baseUrl(catalogUrl).build();
    }

    public CartResponse get(String userId) { return enrich(carts.findByUserId(userId)); }

    public CartResponse add(String userId, AddItemRequest request) {
        CartResponse.Product product = product(request.productId());
        if (!product.availableSizes().contains(request.selectedSize())) throw new IllegalArgumentException("Kích cỡ không hợp lệ");
        if (!product.colorNames().contains(request.selectedColor())) throw new IllegalArgumentException("Màu sắc không hợp lệ");
        Cart cart = carts.findByUserId(userId);
        List<CartItem> items = new ArrayList<>(cart.items());
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            if (item.productId().equals(request.productId()) && item.selectedSize().equals(request.selectedSize())
                    && item.selectedColor().equals(request.selectedColor())) index = i;
        }
        if (index >= 0) {
            CartItem current = items.get(index);
            items.set(index, current.withQuantity(Math.min(99, current.quantity() + request.quantity())));
        } else {
            items.add(new CartItem(UUID.randomUUID().toString(), request.productId(), request.selectedSize(),
                    request.selectedColor(), request.quantity()));
        }
        return enrich(carts.save(new Cart(userId, items)));
    }

    public CartResponse update(String userId, String itemId, int quantity) {
        Cart cart = carts.findByUserId(userId);
        List<CartItem> items = new ArrayList<>(cart.items());
        int index = indexOf(items, itemId);
        items.set(index, items.get(index).withQuantity(quantity));
        return enrich(carts.save(new Cart(userId, items)));
    }

    public CartResponse remove(String userId, String itemId) {
        Cart cart = carts.findByUserId(userId);
        List<CartItem> items = new ArrayList<>(cart.items());
        items.remove(indexOf(items, itemId));
        return enrich(carts.save(new Cart(userId, items)));
    }

    public void clear(String userId) { carts.deleteByUserId(userId); }

    public void consumeItems(String userId, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        Cart current = carts.findByUserId(userId);
        List<CartItem> remaining = current.items().stream()
                .filter(item -> !itemIds.contains(item.id())).toList();
        if (remaining.size() != current.items().size()) carts.save(new Cart(userId, remaining));
    }

    private CartResponse enrich(Cart cart) {
        List<CartResponse.Item> items = cart.items().stream().map(item -> {
            CartResponse.Product product = product(item.productId());
            return new CartResponse.Item(item.id(), product, item.selectedSize(), item.selectedColor(), item.quantity(),
                    product.price().multiply(BigDecimal.valueOf(item.quantity())));
        }).toList();
        BigDecimal subtotal = items.stream().map(CartResponse.Item::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(items, items.stream().mapToInt(CartResponse.Item::quantity).sum(), subtotal,
                BigDecimal.ZERO, BigDecimal.ZERO, subtotal);
    }

    private CartResponse.Product product(String id) {
        CartResponse.Product product = catalog.get().uri("/api/v1/products/{id}", id).retrieve()
                .body(CartResponse.Product.class);
        if (product == null) throw new NotFoundException("Không tìm thấy sản phẩm");
        return product;
    }

    private int indexOf(List<CartItem> items, String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id().equals(id)) return i;
        throw new NotFoundException("Không tìm thấy sản phẩm trong giỏ");
    }
}
