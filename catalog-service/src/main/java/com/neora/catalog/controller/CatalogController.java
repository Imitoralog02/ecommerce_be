package com.neora.catalog.controller;

import com.neora.catalog.dto.ProductResponse;
import com.neora.catalog.service.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/products")
    public CatalogService.PageResponse products(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return catalog.search(keyword, category, page, size);
    }
    @GetMapping("/products/featured")
    public List<ProductResponse> featured(@RequestParam(defaultValue = "8") int size) { return catalog.featured(size); }
    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable String id) { return catalog.get(id); }
    @GetMapping("/categories")
    public List<CatalogService.CategoryResponse> categories() { return catalog.categories(); }
}
