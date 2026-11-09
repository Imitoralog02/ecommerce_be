package com.neora.catalog.flashsale;

import com.neora.catalog.dto.ProductResponse;
import com.neora.catalog.model.Product;
import com.neora.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class FlashSaleService {
    private final FlashSaleRepository campaigns;
    private final ProductRepository products;
    private final FlashSaleAllocationService allocations;

    public FlashSaleService(FlashSaleRepository campaigns, ProductRepository products,
                            FlashSaleAllocationService allocations) {
        this.campaigns = campaigns;
        this.products = products;
        this.allocations = allocations;
    }

    public ProductResponse price(Product product) {
        return price(product, activeCampaigns(Instant.now()));
    }

    public List<ProductResponse> priceAll(List<Product> products) {
        List<FlashSale> active = activeCampaigns(Instant.now());
        return products.stream().map(product -> price(product, active)).toList();
    }

    private ProductResponse price(Product product, List<FlashSale> active) {
        return active.stream().flatMap(campaign -> campaign.offers().stream()
                        .filter(offer -> offer != null && product.id().equals(offer.productId())
                                && validPrice(offer.salePrice(), product.price()) && validQuota(offer.quota()))
                        .map(offer -> new SaleChoice(campaign, offer, allocations.allocation(campaign, offer))))
                .filter(choice -> choice.allocation().available() > 0)
                .min(Comparator.comparing((SaleChoice choice) -> choice.offer().salePrice())
                        .thenComparing(choice -> choice.campaign().id()))
                .map(choice -> withPrice(product, choice)).orElseGet(() -> ProductResponse.from(product));
    }

    public List<CampaignResponse> active() {
        List<FlashSale> active = activeCampaigns(Instant.now());
        return active.stream().map(campaign -> new CampaignResponse(
                campaign.id(), campaign.title(), campaign.startsAt(), campaign.endsAt(),
                campaign.offers().stream().filter(java.util.Objects::nonNull)
                        .map(offer -> products.findById(offer.productId())
                        .filter(Product::active)
                        .filter(product -> validPrice(offer.salePrice(), product.price()) && validQuota(offer.quota()))
                        .map(product -> price(product, active))
                        .filter(product -> campaign.id().equals(product.flashSaleId())).orElse(null))
                        .filter(java.util.Objects::nonNull).toList())).toList();
    }

    private List<FlashSale> activeCampaigns(Instant now) {
        return campaigns.findAll().stream()
                .filter(campaign -> campaign.active() && campaign.startsAt() != null && campaign.endsAt() != null
                        && !now.isBefore(campaign.startsAt()) && now.isBefore(campaign.endsAt())
                        && campaign.offers() != null)
                .sorted(Comparator.comparing(FlashSale::startsAt).reversed()).toList();
    }

    private static boolean validPrice(BigDecimal sale, BigDecimal regular) {
        return sale != null && regular != null && sale.signum() > 0 && sale.compareTo(regular) < 0;
    }

    private static boolean validQuota(Integer quota) {
        return quota == null || (quota > 0 && quota <= 1_000_000);
    }

    private static ProductResponse withPrice(Product product, SaleChoice choice) {
        ProductResponse base = ProductResponse.from(product);
        return new ProductResponse(base.id(), base.pieceNumber(), base.title(), base.brand(),
                base.subCategory(), base.category(), choice.offer().salePrice(), product.price(), base.composition(),
                base.description(), base.availableSizes(), base.availableColors(), base.colorNames(),
                base.imageType(), base.imagePath(), base.tag(), base.isHeroPiece(), base.rating(),
                base.reviewCount(), base.soldCount(), base.createdAt(), base.updatedAt(),
                choice.campaign().id(), choice.allocation().available());
    }

    private record SaleChoice(FlashSale campaign, FlashSale.Offer offer, FlashSaleAllocation allocation) {}

    public record CampaignResponse(String id, String title, Instant startsAt, Instant endsAt,
                                   List<ProductResponse> products) {}
}
