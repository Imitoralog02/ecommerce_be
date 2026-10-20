package com.neora.customer.wishlist.service;

import com.neora.customer.wishlist.dto.CatalogProductResponse;
import com.neora.customer.wishlist.dto.WishlistResponse;
import com.neora.customer.wishlist.model.Wishlist;
import com.neora.customer.wishlist.model.WishlistItem;
import com.neora.customer.wishlist.repository.WishlistRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class WishlistService {
    private static final int MAX_ITEMS = 100;
    private final WishlistRepository wishlists;
    private final CatalogClient catalog;

    public WishlistService(WishlistRepository wishlists, CatalogClient catalog) {
        this.wishlists = wishlists;
        this.catalog = catalog;
    }

    public WishlistResponse get(String userId) {
        return enrich(find(userId));
    }

    public WishlistResponse add(String userId, String rawProductId) {
        String productId = rawProductId.trim();
        CatalogProductResponse product = catalog.getProduct(productId);
        Wishlist wishlist = find(userId);
        if (wishlist.items().stream().anyMatch(item -> item.productId().equals(productId))) {
            return enrich(wishlist);
        }
        List<WishlistItem> items = new ArrayList<>(wishlist.items());
        if (items.size() >= MAX_ITEMS) {
            throw new IllegalArgumentException("Danh sách yêu thích đã đạt giới hạn 100 sản phẩm");
        }
        items.add(0, new WishlistItem(productId, Instant.now()));
        Wishlist saved = wishlists.save(new Wishlist(userId, List.copyOf(items)));
        return enrich(saved, product);
    }

    public WishlistResponse remove(String userId, String productId) {
        Wishlist wishlist = find(userId);
        List<WishlistItem> items = wishlist.items().stream()
                .filter(item -> !item.productId().equals(productId))
                .toList();
        return enrich(wishlists.save(new Wishlist(userId, items)));
    }

    public void clear(String userId) {
        wishlists.deleteById(userId);
    }

    private Wishlist find(String userId) {
        return wishlists.findById(userId).orElseGet(() -> Wishlist.empty(userId));
    }

    private WishlistResponse enrich(Wishlist wishlist) {
        return enrich(wishlist, null);
    }

    private WishlistResponse enrich(Wishlist wishlist, CatalogProductResponse knownProduct) {
        List<WishlistResponse.Item> items = wishlist.items().stream().map(item -> {
            CatalogProductResponse product = knownProduct != null && knownProduct.id().equals(item.productId())
                    ? knownProduct : catalog.getProduct(item.productId());
            return new WishlistResponse.Item(item.productId(), item.addedAt(), product);
        }).toList();
        return new WishlistResponse(items, items.size());
    }
}
