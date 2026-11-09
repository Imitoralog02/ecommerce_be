package com.neora.catalog.flashsale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
public class FlashSaleSeeder implements CommandLineRunner {
    private final FlashSaleRepository campaigns;
    private final boolean enabled;

    public FlashSaleSeeder(FlashSaleRepository campaigns,
                           @Value("${app.flash-sale.seed-demo:true}") boolean enabled) {
        this.campaigns = campaigns;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled || campaigns.existsById("demo_week")) return;
        Instant now = Instant.now();
        try {
            campaigns.insert(new FlashSale("demo_week", "Ưu đãi NÉORA tuần này",
                    now, now.plusSeconds(7 * 24 * 3600), true, List.of(
                    new FlashSale.Offer("prod_dress_wrap", BigDecimal.valueOf(399_000), 20),
                    new FlashSale.Offer("prod_knit_sweater", BigDecimal.valueOf(349_000), 20),
                    new FlashSale.Offer("prod_hoodie_lavender", BigDecimal.valueOf(299_000), 20))));
        } catch (DuplicateKeyException ignored) {
            // Another catalog instance created the demo campaign.
        }
    }
}
