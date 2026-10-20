package com.neora.customer.wishlist.service;

import com.neora.customer.wishlist.dto.CatalogProductResponse;
import com.neora.customer.wishlist.model.Wishlist;
import com.neora.customer.wishlist.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {
    @Mock WishlistRepository repository;
    @Mock CatalogClient catalog;

    @Test
    void addIsIdempotentAndReturnsProductNeededByFlutterUi() {
        WishlistService service = new WishlistService(repository, catalog);
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        when(catalog.getProduct("prod_dress_wrap")).thenReturn(product());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.add("user-1", "prod_dress_wrap");

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo("prod_dress_wrap");
            assertThat(item.product().title()).isEqualTo("Đầm hoa wrap");
        });
        verify(repository).save(any(Wishlist.class));
    }

    @Test
    void removeOnlyChangesCurrentUsersWishlist() {
        WishlistService service = new WishlistService(repository, catalog);
        var item = new com.neora.customer.wishlist.model.WishlistItem(
                "prod_dress_wrap", Instant.parse("2026-10-15T00:00:00Z"));
        when(repository.findById("user-1")).thenReturn(Optional.of(new Wishlist("user-1", List.of(item))));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.remove("user-1", "prod_dress_wrap");

        assertThat(response.items()).isEmpty();
        verify(repository).save(new Wishlist("user-1", List.of()));
        verifyNoInteractions(catalog);
    }

    private CatalogProductResponse product() {
        Instant now = Instant.parse("2026-10-15T00:00:00Z");
        return new CatalogProductResponse("prod_dress_wrap", "01", "Đầm hoa wrap", "NÉORA",
                "ĐẦM", "Đầm", BigDecimal.valueOf(459000), BigDecimal.valueOf(659000), "Voan",
                "Mô tả", List.of("S", "M"), List.of("#E8D9C4"), List.of("Kem Be"), "dress",
                "assets/images/real/p_dress.jpg", "BÁN CHẠY", true, 4.9, 348, 1200, now, now);
    }
}
