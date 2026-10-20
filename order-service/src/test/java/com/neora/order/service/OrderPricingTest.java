package com.neora.order.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPricingTest {
    @Test
    void expressFeeMatchesFlutterAndTotalIsComputedOnServer() {
        var quote = OrderPricing.quote(BigDecimal.valueOf(459_000), "express", null);

        assertThat(quote.shippingFee()).isEqualByComparingTo("30000");
        assertThat(quote.total()).isEqualByComparingTo("489000");
        assertThat(quote.currency()).isEqualTo("VND");
    }

    @Test
    void unsupportedCouponAndPaymentAreRejected() {
        assertThatThrownBy(() -> OrderPricing.quote(BigDecimal.valueOf(459_000), "STANDARD", "NEORA10"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderPricing.paymentType("MOMO"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
