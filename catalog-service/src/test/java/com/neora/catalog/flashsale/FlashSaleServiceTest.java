package com.neora.catalog.flashsale;

import com.neora.catalog.model.Product;
import com.neora.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlashSaleServiceTest {
    private final FlashSaleRepository campaigns = mock(FlashSaleRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final FlashSaleAllocationService allocations = mock(FlashSaleAllocationService.class);
    private final FlashSaleService service = new FlashSaleService(campaigns, products, allocations);

    @Test
    void activeSalePriceIsUsedForCatalogAndCampaignResponse() {
        Product product = product();
        Instant now = Instant.now();
        when(campaigns.findAll()).thenReturn(List.of(new FlashSale("sale", "Ưu đãi tuần này",
                now.minusSeconds(60), now.plusSeconds(3600), true,
                List.of(new FlashSale.Offer(product.id(), BigDecimal.valueOf(399_000))))));
        when(products.findById(product.id())).thenReturn(Optional.of(product));
        when(allocations.allocation(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FlashSaleAllocation("sale:prod_dress_wrap", "sale", product.id(), 20, 20,
                        java.util.Map.of()));

        assertThat(service.price(product).price()).isEqualByComparingTo("399000");
        assertThat(service.priceAll(List.of(product)).getFirst().price()).isEqualByComparingTo("399000");
        assertThat(service.price(product).originalPrice()).isEqualByComparingTo("459000");
        assertThat(service.active().getFirst().products().getFirst().price()).isEqualByComparingTo("399000");
        assertThat(service.price(product).flashSaleId()).isEqualTo("sale");
        assertThat(service.price(product).flashSaleRemaining()).isEqualTo(20);
    }

    @Test
    void exhaustedSaleUsesRegularPrice() {
        Product product = product();
        Instant now = Instant.now();
        when(campaigns.findAll()).thenReturn(List.of(new FlashSale("sale", "Hết lượt",
                now.minusSeconds(60), now.plusSeconds(3600), true,
                List.of(new FlashSale.Offer(product.id(), BigDecimal.valueOf(399_000), 1)))));
        when(allocations.allocation(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FlashSaleAllocation("sale:prod_dress_wrap", "sale", product.id(), 1, 0,
                        java.util.Map.of()));
        when(products.findById(product.id())).thenReturn(Optional.of(product));

        assertThat(service.price(product).price()).isEqualByComparingTo("459000");
        assertThat(service.price(product).flashSaleId()).isNull();
        assertThat(service.active().getFirst().products()).isEmpty();
    }

    @Test
    void endedSaleCannotChangeCheckoutPrice() {
        Product product = product();
        Instant now = Instant.now();
        when(campaigns.findAll()).thenReturn(List.of(new FlashSale("ended", "Đã kết thúc",
                now.minusSeconds(7200), now.minusSeconds(3600), true,
                List.of(new FlashSale.Offer(product.id(), BigDecimal.ONE)))));

        assertThat(service.price(product).price()).isEqualByComparingTo("459000");
        assertThat(service.active()).isEmpty();
    }

    private Product product() {
        Instant now = Instant.now();
        return new Product("prod_dress_wrap", "01", "Đầm", "NÉORA", "Đầm", "Đầm",
                BigDecimal.valueOf(459_000), BigDecimal.valueOf(659_000), "cotton", "Mô tả",
                List.of("M"), List.of("#FFF"), List.of("Trắng"), "dress", "dress.jpg", "NEW",
                true, 4.9, 1, 1, true, now, now);
    }
}
