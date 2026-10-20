package com.neora.customer.wishlist.repository;

import com.neora.customer.wishlist.model.Wishlist;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WishlistRepository extends MongoRepository<Wishlist, String> {}
