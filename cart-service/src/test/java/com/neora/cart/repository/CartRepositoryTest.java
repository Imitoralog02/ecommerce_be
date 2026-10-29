package com.neora.cart.repository;

import com.neora.cart.model.Cart;
import com.neora.cart.model.CartItem;
import com.neora.shared.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CartRepositoryTest {
    private static final String KEY = "cart:user:user-1";
    private static final Duration TTL = Duration.ofDays(30);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void retriesConflictAgainstFreshCartInsteadOfOverwritingIt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisOperations operations = mock(RedisOperations.class);
        ValueOperations values = mock(ValueOperations.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(redis.execute(any(SessionCallback.class))).thenAnswer(invocation ->
                ((SessionCallback<Cart>) invocation.getArgument(0)).execute(operations));
        when(operations.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn("first", "second");
        CartItem existing = new CartItem("existing", "p1", "M", "Đen", 1);
        CartItem concurrent = new CartItem("concurrent", "p2", "S", "Trắng", 1);
        CartItem added = new CartItem("added", "p3", "L", "Xanh", 1);
        when(mapper.readValue("first", Cart.class)).thenReturn(new Cart("user-1", List.of(existing)));
        when(mapper.readValue("second", Cart.class)).thenReturn(new Cart("user-1", List.of(existing, concurrent)));
        when(mapper.writeValueAsString(any(Cart.class))).thenReturn("encoded");
        when(operations.exec()).thenReturn(null, List.of("OK"));
        CartRepository repository = new CartRepository(redis, mapper, TTL);

        Cart result = repository.mutate("user-1", current -> {
            List<CartItem> items = new ArrayList<>(current.items());
            items.add(added);
            return new Cart("user-1", items);
        });

        assertEquals(List.of(existing, concurrent, added), result.items());
        verify(operations, times(2)).watch(KEY);
        verify(operations, times(2)).exec();
        verify(values, times(2)).set(KEY, "encoded", TTL);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void returnsConflictAfterBoundedRetries() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisOperations operations = mock(RedisOperations.class);
        ValueOperations values = mock(ValueOperations.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(redis.execute(any(SessionCallback.class))).thenAnswer(invocation ->
                ((SessionCallback<Cart>) invocation.getArgument(0)).execute(operations));
        when(operations.opsForValue()).thenReturn(values);
        when(mapper.writeValueAsString(any(Cart.class))).thenReturn("encoded");
        when(operations.exec()).thenReturn(null);
        CartRepository repository = new CartRepository(redis, mapper, TTL);
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ConflictException.class, () -> repository.mutate("user-1", current -> {
            calls.incrementAndGet();
            return new Cart("user-1", List.of(new CartItem("new", "p1", "M", "Đen", 1)));
        }));

        assertEquals(8, calls.get());
        verify(operations, times(8)).exec();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void skipsWriteWhenMutationChangesNothing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisOperations operations = mock(RedisOperations.class);
        ValueOperations values = mock(ValueOperations.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(redis.execute(any(SessionCallback.class))).thenAnswer(invocation ->
                ((SessionCallback<Cart>) invocation.getArgument(0)).execute(operations));
        when(operations.opsForValue()).thenReturn(values);
        CartRepository repository = new CartRepository(redis, mapper, TTL);

        assertEquals(Cart.empty("user-1"), repository.mutate("user-1", current -> current));

        verify(operations).unwatch();
        verify(operations, never()).multi();
        verify(operations, never()).exec();
        verify(values, never()).set(any(), any(), any(Duration.class));
    }
}
