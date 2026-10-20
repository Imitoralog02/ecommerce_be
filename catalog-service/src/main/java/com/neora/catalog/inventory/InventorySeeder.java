package com.neora.catalog.inventory;

import com.neora.catalog.model.Product;
import com.neora.catalog.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(2)
public class InventorySeeder implements CommandLineRunner {
    private final ProductRepository products;
    private final MongoTemplate mongo;
    private final int demoQuantity;

    public InventorySeeder(ProductRepository products, MongoTemplate mongo,
                           @Value("${app.inventory.demo-seed-quantity:20}") int demoQuantity) {
        if (demoQuantity < 0) throw new IllegalArgumentException("Số lượng seed tồn kho không được âm");
        this.products = products;
        this.mongo = mongo;
        this.demoQuantity = demoQuantity;
    }

    @Override
    public void run(String... args) {
        if (demoQuantity == 0) return;
        for (Product product : products.findAll()) {
            for (String size : product.availableSizes()) {
                for (String color : product.colorNames()) {
                    String id = InventoryService.variantId(product.id(), size, color);
                    if (mongo.exists(Query.query(Criteria.where("_id").is(id)), InventoryVariant.class)) {
                        continue;
                    }
                    try {
                        mongo.insert(new InventoryVariant(id, product.id(), size, color, demoQuantity, Map.of()));
                    } catch (DuplicateKeyException ignored) {
                        // Một instance khác đã tạo cùng biến thể; không ghi đè tồn kho hiện tại.
                    }
                }
            }
        }
    }
}
