package com.neora.customer.wishlist.service;

import com.neora.customer.wishlist.dto.CatalogProductResponse;
import com.neora.customer.wishlist.model.Wishlist;
import com.neora.customer.wishlist.repository.WishlistRepository;
import com.neora.shared.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {
    @Mock WishlistRepository repository;
    @Mock CatalogClient catalog;
    @Mock MongoTemplate mongo;

    @Test
    void addIsIdempotentAndReturnsProductNeededByFlutterUi() {
        WishlistService service = new WishlistService(repository, catalog, mongo);
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        when(catalog.getProduct("prod_dress_wrap")).thenReturn(product());
        when(repository.insert(any(Wishlist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.add("user-1", "prod_dress_wrap");

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo("prod_dress_wrap");
            assertThat(item.product().title()).isEqualTo("Đầm hoa wrap");
        });
        verify(repository).insert(any(Wishlist.class));
    }

    @Test
    void removeOnlyChangesCurrentUsersWishlist() {
        WishlistService service = new WishlistService(repository, catalog, mongo);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Wishlist.class))).thenReturn(Wishlist.empty("user-1"));

        var response = service.remove("user-1", "prod_dress_wrap");

        assertThat(response.items()).isEmpty();
        verify(mongo).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Wishlist.class));
        verifyNoInteractions(repository);
        verifyNoInteractions(catalog);
    }

    @Test
    void addUsesAtomicConditionalPushForExistingWishlist() {
        WishlistService service = new WishlistService(repository, catalog, mongo);
        when(catalog.getProduct("prod_dress_wrap")).thenReturn(product());
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Wishlist.class))).thenReturn(new Wishlist("user-1", List.of(
                new com.neora.customer.wishlist.model.WishlistItem("prod_dress_wrap", Instant.now()))));

        var response = service.add("user-1", "prod_dress_wrap");

        assertThat(response.totalCount()).isEqualTo(1);
        var query = org.mockito.ArgumentCaptor.forClass(Query.class);
        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class),
                eq(Wishlist.class));
        assertThat(query.getValue().getQueryObject().toJson())
                .contains("items.productId", "$ne", "items.99", "$exists");
        assertThat(update.getValue().getUpdateObject().toString()).contains("$push", "$position");
        verifyNoInteractions(repository);
    }

    @Test
    void duplicateAddReturnsExistingWishlistWithoutWritingAgain() {
        WishlistService service = new WishlistService(repository, catalog, mongo);
        var item = new com.neora.customer.wishlist.model.WishlistItem("prod_dress_wrap", Instant.now());
        when(catalog.getProduct("prod_dress_wrap")).thenReturn(product());
        when(repository.findById("user-1"))
                .thenReturn(Optional.of(new Wishlist("user-1", List.of(item))));

        var response = service.add("user-1", "prod_dress_wrap");

        assertThat(response.totalCount()).isEqualTo(1);
        verify(repository, never()).insert(any(Wishlist.class));
    }

    @Test
    void retriesWhenAnotherRequestCreatesWishlistFirst() {
        WishlistService service = new WishlistService(repository, catalog, mongo);
        var concurrentItem = new com.neora.customer.wishlist.model.WishlistItem("another-product", Instant.now());
        when(catalog.getProduct("prod_dress_wrap")).thenReturn(product());
        when(catalog.getProduct("another-product")).thenReturn(product());
        when(repository.findById("user-1"))
                .thenReturn(Optional.empty(), Optional.of(new Wishlist("user-1", List.of(concurrentItem))));
        when(repository.insert(any(Wishlist.class))).thenThrow(new DuplicateKeyException("concurrent insert"));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Wishlist.class))).thenReturn(null, new Wishlist("user-1", List.of(concurrentItem,
                new com.neora.customer.wishlist.model.WishlistItem("prod_dress_wrap", Instant.now()))));

        var response = service.add("user-1", "prod_dress_wrap");

        assertThat(response.items()).extracting(item -> item.productId())
                .containsExactly("another-product", "prod_dress_wrap");
        verify(mongo, times(2)).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Wishlist.class));
    }

    @Test
    void fullWishlistRejectsNewItem() {
        WishlistService service = new WishlistService(repository, catalog, mongo);
        when(catalog.getProduct("prod_dress_wrap")).thenReturn(product());
        var items = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> new com.neora.customer.wishlist.model.WishlistItem("other-" + index, Instant.now()))
                .toList();
        when(repository.findById("user-1")).thenReturn(Optional.of(new Wishlist("user-1", items)));

        assertThatThrownBy(() -> service.add("user-1", "prod_dress_wrap"))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).insert(any(Wishlist.class));
    }

    private CatalogProductResponse product() {
        Instant now = Instant.parse("2026-10-15T00:00:00Z");
        return new CatalogProductResponse("prod_dress_wrap", "01", "Đầm hoa wrap", "NÉORA",
                "ĐẦM", "Đầm", BigDecimal.valueOf(459000), BigDecimal.valueOf(659000), "Voan",
                "Mô tả", List.of("S", "M"), List.of("#E8D9C4"), List.of("Kem Be"), "dress",
                "assets/images/real/p_dress.jpg", "BÁN CHẠY", true, 4.9, 348, 1200, now, now);
    }
}
