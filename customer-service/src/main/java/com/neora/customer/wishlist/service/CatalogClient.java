package com.neora.customer.wishlist.service;

import com.neora.customer.wishlist.dto.CatalogProductResponse;
import com.neora.shared.error.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient {
    private final RestClient catalog;

    public CatalogClient(RestClient.Builder builder,
                         @Value("${services.catalog-url:http://localhost:8082}") String catalogUrl) {
        this.catalog = builder.baseUrl(catalogUrl).build();
    }

    public CatalogProductResponse getProduct(String productId) {
        try {
            CatalogProductResponse product = catalog.get().uri("/api/v1/products/{id}", productId)
                    .retrieve().body(CatalogProductResponse.class);
            if (product == null) throw new NotFoundException("Không tìm thấy sản phẩm");
            return product;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new NotFoundException("Không tìm thấy sản phẩm");
        }
    }
}
