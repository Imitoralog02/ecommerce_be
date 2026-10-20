package com.neora.catalog.config;

import com.neora.catalog.model.Product;
import com.neora.catalog.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class CatalogSeeder implements CommandLineRunner {
    private final ProductRepository products;
    private final boolean syncLegacyDemo;
    private static final Map<String, List<String>> LEGACY_COLORS = Map.of(
            "prod_dress_wrap", List.of("Kem Be", "Camel Nâu"),
            "prod_knit_sweater", List.of("Be Yến Mạch", "Kem Sữa"),
            "prod_hoodie_lavender", List.of("Lavender", "Xám Tiêu"),
            "prod_linen_shirt", List.of("Trắng Basic", "Be Tự Nhiên"),
            "prod_jeans_wide", List.of("Xanh Wash Nhạt"),
            "prod_sneaker_white", List.of("Trắng"),
            "prod_leather_bag", List.of("Nâu Bò", "Đen Tuyển"),
            "prod_dress_floral_side", List.of("Kem Be Thơ"));

    public CatalogSeeder(ProductRepository products,
                         @Value("${app.catalog.sync-legacy-demo:false}") boolean syncLegacyDemo) {
        this.products = products;
        this.syncLegacyDemo = syncLegacyDemo;
    }
    @Override public void run(String... args) {
        Instant now = Instant.now();
        List<Product> demo = demoProducts(now);
        if (products.count() == 0) {
            products.saveAll(demo);
            return;
        }
        if (!syncLegacyDemo) return;
        for (Product expected : demo) {
            products.findById(expected.id()).filter(current -> isLegacyDemo(current, expected))
                    .ifPresent(current -> products.save(new Product(expected.id(), expected.pieceNumber(),
                            expected.title(), expected.brand(), expected.subCategory(), expected.category(),
                            expected.price(), expected.originalPrice(), expected.composition(), expected.description(),
                            expected.availableSizes(), expected.availableColors(), expected.colorNames(), expected.imageType(),
                            expected.imagePath(), expected.tag(), expected.heroPiece(), expected.rating(),
                            expected.reviewCount(), expected.soldCount(), expected.active(), current.createdAt(), now)));
        }
    }
    private boolean isLegacyDemo(Product current, Product expected) {
        return expected.pieceNumber().equals(current.pieceNumber())
                && current.active()
                && expected.imagePath().equals(current.imagePath())
                && expected.price().compareTo(current.price()) == 0
                && "Chất liệu cao cấp".equals(current.composition())
                && "Sản phẩm thời trang NÉORA".equals(current.description())
                && LEGACY_COLORS.get(expected.id()).equals(current.colorNames());
    }

    static List<Product> demoProducts(Instant now) {
        return List.of(
                product("prod_dress_wrap", "01", "Đầm hoa wrap dáng midi nữ tính", "NÉORA Collection", "Đầm", 459000, 659000, List.of("S","M","L","XL"), List.of("#E8D9C4","#C8A97E"), List.of("Kem Be","Camel Nâu"), "assets/images/real/p_dress.jpg", true, now),
                product("prod_knit_sweater", "02", "Áo khoác linen cổ lọ dệt mịn", "NÉORA Studio", "Áo len", 389000, 520000, List.of("S","M","L"), List.of("#566349","#E5DFC9"), List.of("Xanh Rêu","Be Yến Mạch"), "assets/images/real/p_knit.jpg", false, now),
                product("prod_hoodie_lavender", "03", "Áo hoodie oversize cotton tự nhiên", "NÉORA Everyday", "Hoodie", 329000, 450000, List.of("M","L","XL"), List.of("#D8D3C8","#566349"), List.of("Be Cát","Xanh Rêu"), "assets/images/real/p_hoodie.jpg", false, now),
                product("prod_linen_shirt", "04", "Sơ mi cotton dáng rộng thoáng mát", "NÉORA Studio", "Sơ mi", 279000, 350000, List.of("S","M","L","XL"), List.of("#F3EFE6","#C8B29B","#DCE3E6"), List.of("Trắng Ngà","Be Cát","Xanh Nhạt"), "assets/images/real/p_shirt.jpg", false, now),
                product("prod_jeans_wide", "05", "Quần âu ống suông lưng cao", "NÉORA Tailoring", "Quần", 399000, 499000, List.of("26","27","28","29"), List.of("#3E403D","#D8D0C5"), List.of("Xám Than","Kem Đá"), "assets/images/real/p_jeans.jpg", false, now),
                product("prod_sneaker_white", "06", "Giày sneaker da trắng basic", "NÉORA Footwear", "Giày", 690000, 890000, List.of("36","37","38","39","40"), List.of("#F7F5F0"), List.of("Trắng Kem"), "assets/images/real/p_sneaker.jpg", false, now),
                product("prod_leather_bag", "07", "Túi đeo vai da mềm khoá vàng", "NÉORA Accessories", "Túi", 590000, 790000, List.of("Free size"), List.of("#8B5A2B","#222220"), List.of("Nâu Da Bò","Đen"), "assets/images/real/p_bag.jpg", false, now),
                product("prod_dress_floral_side", "08", "Đầm linen suông cổ chữ V tự nhiên", "NÉORA Collection", "Đầm", 499000, 699000, List.of("S","M","L"), List.of("#E8D9C4"), List.of("Kem Tự Nhiên"), "assets/images/real/p_dress_side.jpg", false, now));
    }
    private static Product product(String id, String no, String title, String brand, String category, long price, long old,
                            List<String> sizes, List<String> colors, List<String> colorNames, String image, boolean hero, Instant now) {
        DemoMetadata metadata = metadata(id);
        return new Product(id, no, title, brand, metadata.subCategory(), category, BigDecimal.valueOf(price),
                BigDecimal.valueOf(old), metadata.composition(), metadata.description(), sizes,
                colors, colorNames, metadata.imageType(), image, metadata.tag(),
                hero, metadata.rating(), metadata.reviewCount(), metadata.soldCount(), true, now, now);
    }

    private static DemoMetadata metadata(String id) {
        return switch (id) {
            case "prod_dress_wrap" -> new DemoMetadata("NỮ • ĐẦM", "Voan tơ mềm mại thoáng mát · Lót lụa mềm",
                    "Đầm dáng wrap tôn dáng, chất liệu mềm mại thoáng mát phù hợp dạo phố hay đi làm nhẹ nhàng.",
                    "dress", "BÁN CHẠY", 4.9, 348, 1200);
            case "prod_knit_sweater" -> new DemoMetadata("NAM • ÁO LEN", "Linen & sợi dệt tự nhiên thoáng nhẹ",
                    "Áo khoác dệt phom rộng mềm mại, tông màu rêu nhã nhặn dễ phối đồ mỗi ngày.",
                    "knit_sweater", "DỄ PHỐI", 4.8, 178, 860);
            case "prod_hoodie_lavender" -> new DemoMetadata("UNISEX • HOODIE", "Cotton tự nhiên dày dặn thoáng khí",
                    "Phom dáng rộng thoải mái, tông màu trung tính dễ mặc trong nhịp sống thường ngày.",
                    "hoodie", "MỚI", 4.9, 245, 2100);
            case "prod_linen_shirt" -> new DemoMetadata("NỮ • ÁO SƠ MI", "100% Cotton hữu cơ thoáng khí",
                    "Áo sơ mi cổ bẻ phom thoải mái, chất vải dệt tự nhiên thấm hút mồ hôi tối đa, phong cách tối giản thanh lịch.",
                    "shirt", "DỄ PHỐI", 4.8, 126, 640);
            case "prod_jeans_wide" -> new DemoMetadata("NỮ • QUẦN", "Vải tuyết mưa pha rayon đứng dáng",
                    "Quần lưng cao ống suông che khuyết điểm, tông xám than thanh lịch dễ phối cùng sơ mi.",
                    "jeans", "BÁN CHẠY", 4.8, 310, 1500);
            case "prod_sneaker_white" -> new DemoMetadata("PHỤ KIỆN • GIÀY", "Da mềm êm ái · Đế cao su đúc nguyên khối",
                    "Giày trắng thiết kế tối giản, đệm lót êm chân hỗ trợ di chuyển cả ngày, dễ dàng phối với mọi outfit.",
                    "sneaker", "DỄ PHỐI", 4.9, 520, 3200);
            case "prod_leather_bag" -> new DemoMetadata("PHỤ KIỆN • TÚI", "Da vân mịn cao cấp · Khoá mạ vàng ấm",
                    "Túi đeo vai phom gọn thanh lịch, quai đeo êm ái, chứa vừa điện thoại và đồ dùng hàng ngày.",
                    "bag", "ĐIỂM NHẤN", 4.9, 188, 930);
            case "prod_dress_floral_side" -> new DemoMetadata("NỮ • ĐẦM", "100% Linen tự nhiên đã xử lý mềm",
                    "Mẫu đầm suông nhẹ nhàng, thoáng mát và tự nhiên theo từng bước chân.",
                    "dress", "MỚI", 4.8, 119, 720);
            default -> throw new IllegalArgumentException("Không có metadata demo cho sản phẩm " + id);
        };
    }

    private record DemoMetadata(String subCategory, String composition, String description,
                                String imageType, String tag, double rating, int reviewCount, int soldCount) {}
}
