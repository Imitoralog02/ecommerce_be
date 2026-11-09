package com.neora.order.promotion;

import com.neora.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {
    private final PromotionService promotions;

    public PromotionController(PromotionService promotions) {
        this.promotions = promotions;
    }

    @GetMapping
    public List<PromotionResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return promotions.list(user.id());
    }
}
