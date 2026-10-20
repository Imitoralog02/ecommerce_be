package com.neora.catalog.config;

import com.neora.catalog.model.Product;
import com.neora.catalog.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
@Order(1)
public class CatalogSeeder implements CommandLineRunner {
    private final ProductRepository products;
    public CatalogSeeder(ProductRepository products) { this.products = products; }
    @Override public void run(String... args) {
        if (products.count() > 0) return;
        Instant now = Instant.now();
        products.saveAll(List.of(
                product("prod_dress_wrap", "01", "Đầm hoa wrap dáng midi nữ tính", "NÉORA Collection", "Đầm", 459000, 659000, List.of("S","M","L","XL"), List.of("Kem Be","Camel Nâu"), "assets/images/real/p_dress.jpg", true, now),
                product("prod_knit_sweater", "02", "Áo len cổ lọ dệt kim mềm mịn", "NÉORA Studio", "Áo len", 389000, 520000, List.of("S","M","L"), List.of("Be Yến Mạch","Kem Sữa"), "assets/images/real/p_knit.jpg", false, now),
                product("prod_hoodie_lavender", "03", "Áo hoodie oversize màu lavender", "NÉORA Street", "Hoodie", 329000, 450000, List.of("M","L","XL"), List.of("Lavender","Xám Tiêu"), "assets/images/real/p_hoodie.jpg", false, now),
                product("prod_linen_shirt", "04", "Sơ mi linen cộc tay thoáng mát", "NÉORA Men", "Sơ mi", 279000, 350000, List.of("S","M","L","XL"), List.of("Trắng Basic","Be Tự Nhiên"), "assets/images/real/p_shirt.jpg", false, now),
                product("prod_jeans_wide", "05", "Quần jeans ống rộng wash nhạt", "NÉORA Denim", "Quần", 399000, 499000, List.of("26","27","28","29"), List.of("Xanh Wash Nhạt"), "assets/images/real/p_jeans.jpg", false, now),
                product("prod_sneaker_white", "06", "Giày sneaker da trắng basic", "NÉORA Footwear", "Giày", 690000, 890000, List.of("36","37","38","39","40"), List.of("Trắng"), "assets/images/real/p_sneaker.jpg", false, now),
                product("prod_leather_bag", "07", "Túi đeo vai da khoá vàng sang trọng", "NÉORA Accessories", "Túi", 590000, 790000, List.of("Free size"), List.of("Nâu Bò","Đen Tuyển"), "assets/images/real/p_bag.jpg", false, now),
                product("prod_dress_floral_side", "08", "Váy hoa xoè dáng dài thướt tha", "NÉORA Collection", "Đầm", 499000, 699000, List.of("S","M","L"), List.of("Kem Be Thơ"), "assets/images/real/p_dress_side.jpg", false, now)
        ));
    }
    private Product product(String id, String no, String title, String brand, String category, long price, long old,
                            List<String> sizes, List<String> colorNames, String image, boolean hero, Instant now) {
        return new Product(id, no, title, brand, category.toUpperCase(), category, BigDecimal.valueOf(price),
                BigDecimal.valueOf(old), "Chất liệu cao cấp", "Sản phẩm thời trang NÉORA", sizes,
                List.of("#E8D9C4"), colorNames, category.toLowerCase(), image, hero ? "BÁN CHẠY" : "MỚI",
                hero, 4.9, 100, 500, true, now, now);
    }
}
