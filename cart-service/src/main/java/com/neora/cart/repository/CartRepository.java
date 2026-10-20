package com.neora.cart.repository;

import com.neora.cart.model.Cart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Repository
public class CartRepository {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public CartRepository(StringRedisTemplate redis, ObjectMapper mapper,
                          @Value("${app.cart.ttl:P30D}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    public Cart findByUserId(String userId) {
        String value = redis.opsForValue().get(key(userId));
        if (value == null) return Cart.empty(userId);
        try { return mapper.readValue(value, Cart.class); }
        catch (Exception ex) { throw new IllegalStateException("Không thể đọc giỏ hàng", ex); }
    }

    public Cart save(Cart cart) {
        try {
            redis.opsForValue().set(key(cart.userId()), mapper.writeValueAsString(cart), ttl);
            return cart;
        } catch (Exception ex) { throw new IllegalStateException("Không thể lưu giỏ hàng", ex); }
    }

    public void deleteByUserId(String userId) { redis.delete(key(userId)); }
    private String key(String userId) { return "cart:user:" + userId; }
}
