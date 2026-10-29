package com.neora.cart.repository;

import com.neora.cart.model.Cart;
import com.neora.shared.error.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.function.UnaryOperator;

@Repository
public class CartRepository {
    private static final int MAX_UPDATE_ATTEMPTS = 8;
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
        return decode(userId, redis.opsForValue().get(key(userId)));
    }

    // WATCH keeps the read, mutation and write on one Redis connection. A failed EXEC
    // means another request changed this cart, so the mutation is applied again to fresh data.
    public Cart mutate(String userId, UnaryOperator<Cart> mutation) {
        String cartKey = key(userId);
        return redis.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Cart execute(RedisOperations operations) {
                for (int attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
                    operations.watch(cartKey);
                    boolean transactionStarted = false;
                    try {
                        Cart current = decode(userId, (String) operations.opsForValue().get(cartKey));
                        Cart next = mutation.apply(current);
                        if (!userId.equals(next.userId())) throw new IllegalArgumentException("Sai chủ sở hữu giỏ hàng");
                        if (next.equals(current)) {
                            operations.unwatch();
                            return current;
                        }
                        String value = encode(next);
                        operations.multi();
                        transactionStarted = true;
                        operations.opsForValue().set(cartKey, value, ttl);
                        List<Object> result = operations.exec();
                        transactionStarted = false;
                        if (result != null) return next;
                    } catch (RuntimeException ex) {
                        if (transactionStarted) operations.discard();
                        else operations.unwatch();
                        throw ex;
                    }
                }
                throw new ConflictException("Giỏ hàng đang được cập nhật, vui lòng thử lại");
            }
        });
    }

    private Cart decode(String userId, String value) {
        if (value == null) return Cart.empty(userId);
        try { return mapper.readValue(value, Cart.class); }
        catch (Exception ex) { throw new IllegalStateException("Không thể đọc giỏ hàng", ex); }
    }

    private String encode(Cart cart) {
        try { return mapper.writeValueAsString(cart); }
        catch (Exception ex) { throw new IllegalStateException("Không thể lưu giỏ hàng", ex); }
    }

    private String key(String userId) { return "cart:user:" + userId; }
}
