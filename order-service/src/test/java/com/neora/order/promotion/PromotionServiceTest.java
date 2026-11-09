package com.neora.order.promotion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionServiceTest {
    private final PromotionRepository repository = mock(PromotionRepository.class);
    private final PromotionService service = new PromotionService(repository);

    @Test
    void percentDiscountIsCappedAndCodeIsNormalized() {
        when(repository.findById("NEORA10")).thenReturn(java.util.Optional.of(promotion(
                "NEORA10", Promotion.DiscountType.PERCENT, "10", "200000", "100000", null, null)));
        var quote = service.quote(new BigDecimal("1500000"), "EXPRESS", " neora10 ");
        assertThat(quote.discount()).isEqualByComparingTo("100000");
        assertThat(quote.total()).isEqualByComparingTo("1430000");
    }

    @Test
    void freeShippingOnlyDiscountsPaidShipping() {
        var freeShipping = promotion("FREESHIP", Promotion.DiscountType.SHIPPING,
                "30000", "0", null, null, null);
        when(repository.findById("FREESHIP")).thenReturn(java.util.Optional.of(freeShipping));
        assertThat(service.quote(new BigDecimal("459000"), "EXPRESS", "FREESHIP").total())
                .isEqualByComparingTo("459000");
        assertThatThrownBy(() -> service.quote(new BigDecimal("459000"), "STANDARD", "FREESHIP"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không áp dụng");
    }

    @Test
    void inactiveExpiredAndBelowMinimumAreRejected() {
        var base = service.quote(new BigDecimal("200000"), "STANDARD", null);
        assertThatThrownBy(() -> PromotionService.apply(base, promotion("X", Promotion.DiscountType.FIXED,
                "30000", "299000", null, null, null), Instant.now()))
                .hasMessageContaining("tối thiểu");
        assertThatThrownBy(() -> PromotionService.apply(base, promotion("X", Promotion.DiscountType.FIXED,
                "30000", "0", null, null, Instant.EPOCH), Instant.now()))
                .hasMessageContaining("hết hạn");
        assertThatThrownBy(() -> PromotionService.apply(base,
                new Promotion("X", "Tắt", Promotion.DiscountType.FIXED, BigDecimal.valueOf(30_000),
                        BigDecimal.ZERO, null, null, null, false), Instant.now()))
                .hasMessageContaining("hết hạn");
    }

    private Promotion promotion(String code, Promotion.DiscountType type, String value,
                                String minimum, String cap, Instant start, Instant end) {
        return new Promotion(code, code, type, new BigDecimal(value), new BigDecimal(minimum),
                cap == null ? null : new BigDecimal(cap), start, end, true);
    }
}
