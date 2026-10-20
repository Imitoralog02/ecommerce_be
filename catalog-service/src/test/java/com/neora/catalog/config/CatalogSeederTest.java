package com.neora.catalog.config;

import com.neora.catalog.model.Product;
import com.neora.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogSeederTest {
    @Test
    void demoVariantsMatchFlutterCatalog() {
        var products = CatalogSeeder.demoProducts(Instant.parse("2026-10-21T00:00:00Z"));
        assertThat(products).hasSize(8);
        var byId = products.stream().collect(Collectors.toMap(Product::id, product -> product));
        assertThat(byId.get("prod_knit_sweater").colorNames()).containsExactly("Xanh Rêu", "Be Yến Mạch");
        assertThat(byId.get("prod_hoodie_lavender").colorNames()).containsExactly("Be Cát", "Xanh Rêu");
        assertThat(byId.get("prod_linen_shirt").colorNames()).containsExactly("Trắng Ngà", "Be Cát", "Xanh Nhạt");
        assertThat(byId.get("prod_jeans_wide").colorNames()).containsExactly("Xám Than", "Kem Đá");
        assertThat(byId.get("prod_sneaker_white").colorNames()).containsExactly("Trắng Kem");
        assertThat(byId.get("prod_leather_bag").colorNames()).containsExactly("Nâu Da Bò", "Đen");
        assertThat(byId.get("prod_dress_floral_side").colorNames()).containsExactly("Kem Tự Nhiên");
        assertThat(byId.get("prod_knit_sweater").availableColors()).containsExactly("#566349", "#E5DFC9");
        assertThat(byId.get("prod_knit_sweater").composition()).isEqualTo("Linen & sợi dệt tự nhiên thoáng nhẹ");
    }

    @Test
    void existingCatalogIsUntouchedWithoutExplicitMigration() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.count()).thenReturn(8L);

        new CatalogSeeder(repository, false).run();

        verify(repository, never()).save(any(Product.class));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void optInMigrationOnlyUpdatesRecognizedLegacySeed() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.count()).thenReturn(8L);
        Product expected = CatalogSeeder.demoProducts(Instant.now()).stream()
                .filter(value -> value.id().equals("prod_knit_sweater")).findFirst().orElseThrow();
        Product legacy = new Product(expected.id(), expected.pieceNumber(), "Áo len cổ lọ dệt kim mềm mịn",
                expected.brand(), "ÁO LEN", expected.category(), expected.price(), expected.originalPrice(),
                "Chất liệu cao cấp", "Sản phẩm thời trang NÉORA", expected.availableSizes(),
                List.of("#E8D9C4"), List.of("Be Yến Mạch", "Kem Sữa"), "áo len", expected.imagePath(),
                "MỚI", false, 4.9, 100, 500, true, expected.createdAt(), expected.updatedAt());
        when(repository.findById("prod_knit_sweater")).thenReturn(Optional.of(legacy));
        when(repository.findById(argThat(id -> !id.equals("prod_knit_sweater")))).thenReturn(Optional.empty());

        new CatalogSeeder(repository, true).run();

        var saved = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().colorNames()).containsExactly("Xanh Rêu", "Be Yến Mạch");
        assertThat(saved.getValue().createdAt()).isEqualTo(legacy.createdAt());
    }
}
