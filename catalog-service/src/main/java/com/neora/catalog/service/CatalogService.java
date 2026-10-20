package com.neora.catalog.service;

import com.neora.catalog.dto.ProductResponse;
import com.neora.catalog.model.Product;
import com.neora.catalog.repository.ProductRepository;
import com.neora.shared.error.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class CatalogService {
    private final ProductRepository products;
    private final MongoTemplate mongo;

    public CatalogService(ProductRepository products, MongoTemplate mongo) {
        this.products = products;
        this.mongo = mongo;
    }

    public PageResponse search(String keyword, String category, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Query query = new Query(Criteria.where("active").is(true));
        if (category != null && !category.isBlank()) {
            query.addCriteria(Criteria.where("category").regex("^" + Pattern.quote(category) + "$", "i"));
        }
        if (keyword != null && !keyword.isBlank()) {
            String escaped = Pattern.quote(keyword.trim());
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(escaped, "i"), Criteria.where("brand").regex(escaped, "i")));
        }
        long total = mongo.count(Query.of(query).limit(-1).skip(-1), Product.class);
        query.with(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ProductResponse> content = mongo.find(query, Product.class).stream().map(ProductResponse::from).toList();
        return new PageResponse(content, safePage, safeSize, total,
                total == 0 ? 0 : (int) Math.ceil((double) total / safeSize));
    }

    @Cacheable(cacheNames = "product", key = "#id")
    public ProductResponse get(String id) {
        return ProductResponse.from(products.findById(id).filter(Product::active)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm")));
    }

    public List<ProductResponse> featured(int size) {
        return products.findByActiveTrueOrderByCreatedAtDesc().stream()
                .sorted((left, right) -> Boolean.compare(right.heroPiece(), left.heroPiece()))
                .limit(Math.min(20, Math.max(1, size))).map(ProductResponse::from).toList();
    }

    public List<CategoryResponse> categories() {
        return products.findByActiveTrueOrderByCreatedAtDesc().stream()
                .collect(java.util.stream.Collectors.groupingBy(Product::category, java.util.stream.Collectors.counting()))
                .entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new CategoryResponse(entry.getKey(), entry.getValue())).toList();
    }

    public record PageResponse(List<ProductResponse> content, int page, int size, long totalElements, int totalPages) {}
    public record CategoryResponse(String name, long productCount) {}
}
