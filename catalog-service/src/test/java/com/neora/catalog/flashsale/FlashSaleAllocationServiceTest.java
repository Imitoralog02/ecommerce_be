package com.neora.catalog.flashsale;

import com.neora.shared.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlashSaleAllocationServiceTest {
    private static final String ORDER = "ord_" + "a".repeat(64);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final FlashSaleRepository campaigns = mock(FlashSaleRepository.class);
    private final FlashSaleAllocationService service = new FlashSaleAllocationService(mongo, campaigns);
    private final FlashSale.Offer offer = new FlashSale.Offer("product-1", BigDecimal.valueOf(399_000), 2);

    @Test
    void quotaIsReservedWithAnAtomicConditionalUpdate() {
        campaign();
        FlashSaleAllocation available = allocation(2, Map.of());
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(FlashSaleAllocation.class)))
                .thenReturn(available);
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(FlashSaleAllocation.class)))
                .thenReturn(available);

        service.reserve(ORDER, "sale", "product-1", 1, offer.salePrice());

        var query = org.mockito.ArgumentCaptor.forClass(Query.class);
        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), eq(FlashSaleAllocation.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("available", "reservations." + ORDER);
        assertThat(update.getValue().getUpdateObject().toJson()).contains("-1", "reservations." + ORDER);
    }

    @Test
    void fullQuotaRejectsSecondOrderAndRetryDoesNotChargeTwice() {
        campaign();
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(FlashSaleAllocation.class)))
                .thenReturn(allocation(0, Map.of()));
        assertThatThrownBy(() -> service.reserve(ORDER, "sale", "product-1", 1, offer.salePrice()))
                .isInstanceOf(ConflictException.class);

        reset(mongo);
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(FlashSaleAllocation.class)))
                .thenReturn(allocation(0, Map.of(ORDER, 1)));
        service.reserve(ORDER, "sale", "product-1", 1, offer.salePrice());
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), eq(FlashSaleAllocation.class));
    }

    @Test
    void releaseRestoresQuotaOnlyForMatchingReservation() {
        service.release(ORDER, "sale", "product-1", 1);
        var query = org.mockito.ArgumentCaptor.forClass(Query.class);
        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(FlashSaleAllocation.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("reservations." + ORDER);
        assertThat(update.getValue().getUpdateObject().toJson()).contains("$inc", "$unset", "available");
    }

    private void campaign() {
        when(campaigns.findById("sale")).thenReturn(Optional.of(new FlashSale("sale", "Sale",
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), true, List.of(offer))));
    }

    private FlashSaleAllocation allocation(int available, Map<String, Integer> reservations) {
        return new FlashSaleAllocation("sale:product-1", "sale", "product-1", 2, available, reservations);
    }
}
