package com.neora.catalog.flashsale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flash-sales")
public class FlashSaleController {
    private final FlashSaleService flashSales;

    public FlashSaleController(FlashSaleService flashSales) { this.flashSales = flashSales; }

    @GetMapping("/active")
    public List<FlashSaleService.CampaignResponse> active() { return flashSales.active(); }
}
