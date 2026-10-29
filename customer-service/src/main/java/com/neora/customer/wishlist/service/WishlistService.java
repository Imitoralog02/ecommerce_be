package com.neora.customer.wishlist.service;

import com.neora.customer.wishlist.dto.CatalogProductResponse;
import com.neora.customer.wishlist.dto.WishlistResponse;
import com.neora.customer.wishlist.model.Wishlist;
import com.neora.customer.wishlist.model.WishlistItem;
import com.neora.customer.wishlist.repository.WishlistRepository;
import com.neora.shared.error.ConflictException;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class WishlistService {
    private static final int MAX_ITEMS = 100;
    private static final int MAX_UPDATE_ATTEMPTS = 8;
    private final WishlistRepository wishlists;
    private final CatalogClient catalog;
    private final MongoTemplate mongo;

    public WishlistService(WishlistRepository wishlists, CatalogClient catalog, MongoTemplate mongo) {
        this.wishlists = wishlists;
        this.catalog = catalog;
        this.mongo = mongo;
    }

    public WishlistResponse get(String userId) {
        return enrich(find(userId));
    }

    public WishlistResponse add(String userId, String rawProductId) {
        String productId = rawProductId.trim();
        CatalogProductResponse product = catalog.getProduct(productId);
        WishlistItem item = new WishlistItem(productId, Instant.now());
        for (int attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            Query eligible = Query.query(Criteria.where("_id").is(userId)
                    .and("items.productId").ne(productId)
                    .and("items.99").exists(false));
            Wishlist updated = mongo.findAndModify(eligible,
                    new Update().push("items").atPosition(0).value(item),
                    FindAndModifyOptions.options().returnNew(true), Wishlist.class);
            if (updated != null) return enrich(updated, product);

            Wishlist current = find(userId);
            if (current.items().stream().anyMatch(existing -> existing.productId().equals(productId))) {
                return enrich(current, product);
            }
            if (current.items().size() >= MAX_ITEMS) {
                throw new ConflictException("Danh sách yêu thích đã đạt giới hạn 100 sản phẩm");
            }
            if (!wishlists.existsById(userId)) {
                try {
                    return enrich(wishlists.insert(new Wishlist(userId, List.of(item))), product);
                } catch (DuplicateKeyException concurrentInsert) {
                    // Another request created the document first; retry against its items.
                }
            }
        }
        throw new ConflictException("Danh sách yêu thích đang được cập nhật, vui lòng thử lại");
    }

    public WishlistResponse remove(String userId, String productId) {
        Wishlist updated = mongo.findAndModify(Query.query(Criteria.where("_id").is(userId)),
                new Update().pull("items", new Document("productId", productId)),
                FindAndModifyOptions.options().returnNew(true), Wishlist.class);
        return enrich(updated == null ? Wishlist.empty(userId) : updated);
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
