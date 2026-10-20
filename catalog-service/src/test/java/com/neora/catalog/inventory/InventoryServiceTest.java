package com.neora.catalog.inventory;

import com.neora.catalog.model.Product;
import com.neora.catalog.repository.ProductRepository;
import com.neora.shared.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    private static final String ORDER_ID = "ord_" + "a".repeat(64);
    private MongoTemplate mongo;
    private ProductRepository products;
    private InventoryService inventory;
    private InventoryService.Line line;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        products = mock(ProductRepository.class);
        inventory = new InventoryService(mongo, products);
        line = new InventoryService.Line("product-1", "M", "Đen", 2);
        Product product = mock(Product.class);
        when(product.active()).thenReturn(true);
        when(product.availableSizes()).thenReturn(List.of("M"));
        when(product.colorNames()).thenReturn(List.of("Đen"));
        when(products.findById("product-1")).thenReturn(Optional.of(product));
    }

    @Test
    void reserveUsesAtomicConditionalUpdate() {
        InventoryVariant stock = new InventoryVariant("sku", "product-1", "M", "Đen", 5, Map.of());
        when(mongo.findById(anyString(), eq(InventoryVariant.class))).thenReturn(stock);
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(InventoryVariant.class)))
                .thenReturn(stock);

        inventory.reserve(ORDER_ID, List.of(line));

        var query = org.mockito.ArgumentCaptor.forClass(Query.class);
        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), eq(InventoryVariant.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("available", "reservations." + ORDER_ID);
        assertThat(update.getValue().getUpdateObject().toJson()).contains("available", "-2", "reservations." + ORDER_ID);
    }

    @Test
    void sameOrderReservationIsIdempotent() {
        when(mongo.findById(anyString(), eq(InventoryVariant.class))).thenReturn(
                new InventoryVariant("sku", "product-1", "M", "Đen", 3, Map.of(ORDER_ID, 2)));

        inventory.reserve(ORDER_ID, List.of(line));

        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), eq(InventoryVariant.class));
    }

    @Test
    void insufficientStockReturnsConflictAndReleasesPartialReservation() {
        when(mongo.findById(anyString(), eq(InventoryVariant.class))).thenReturn(
                new InventoryVariant("sku", "product-1", "M", "Đen", 0, Map.of()));

        assertThatThrownBy(() -> inventory.reserve(ORDER_ID, List.of(line)))
                .isInstanceOf(ConflictException.class);
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(InventoryVariant.class));
    }

    @Test
    void invalidVariantDoesNotTouchStock() {
        assertThatThrownBy(() -> inventory.reserve(ORDER_ID,
                List.of(new InventoryService.Line("product-1", "XL", "Đen", 1))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mongo);
    }
}
