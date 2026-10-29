package com.neora.customer.address.service;

import com.neora.customer.address.dto.AddressRequest;
import com.neora.customer.address.model.ShippingAddress;
import com.neora.customer.address.repository.ShippingAddressRepository;
import com.neora.shared.error.ConflictException;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    @Mock ShippingAddressRepository repository;
    @Mock MongoTemplate mongo;

    @Test
    void firstAddressBecomesDefaultEvenWhenRequestDoesNotAskForIt() {
        AddressService service = new AddressService(repository, mongo);
        when(repository.countByUserId("user-1")).thenReturn(0L);
        when(repository.insert(any(ShippingAddress.class))).thenAnswer(invocation -> {
            ShippingAddress value = invocation.getArgument(0);
            return new ShippingAddress("address-1", value.userId(), value.recipientName(), value.phoneNumber(),
                    value.streetAddress(), value.ward(), value.district(), value.city(), value.isDefault(),
                    value.label(), value.createdAt(), value.updatedAt());
        });

        var response = service.create("user-1", request(false));

        assertThat(response.isDefault()).isTrue();
        assertThat(response.recipientName()).isEqualTo("Nguyễn An");
        verify(mongo).updateMulti(any(Query.class), any(Update.class), eq(ShippingAddress.class));
    }

    @Test
    void settingDefaultClearsPreviousDefaultForSameUser() {
        AddressService service = new AddressService(repository, mongo);
        ShippingAddress selected = address("new", false);
        when(repository.findByIdAndUserId("new", "user-1")).thenReturn(java.util.Optional.of(selected));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ShippingAddress.class))).thenReturn(selected.withDefault(true));

        var response = service.setDefault("user-1", "new");

        assertThat(response.isDefault()).isTrue();
        ArgumentCaptor<Query> clearQuery = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateMulti(clearQuery.capture(), any(Update.class), eq(ShippingAddress.class));
        assertThat(clearQuery.getValue().getQueryObject().toJson()).contains("userId", "isDefault", "$ne", "new");
        verify(mongo).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ShippingAddress.class));
    }

    @Test
    void concurrentFirstAddressFallsBackToNonDefault() {
        AddressService service = new AddressService(repository, mongo);
        when(repository.countByUserId("user-1")).thenReturn(0L);
        when(repository.insert(any(ShippingAddress.class)))
                .thenThrow(new DuplicateKeyException("another default was inserted"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create("user-1", request(false));

        assertThat(response.isDefault()).isFalse();
        ArgumentCaptor<ShippingAddress> inserted = ArgumentCaptor.forClass(ShippingAddress.class);
        verify(repository, times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues()).extracting(ShippingAddress::isDefault)
                .containsExactly(true, false);
    }

    @Test
    void competingDefaultSelectionReturnsConflict() {
        AddressService service = new AddressService(repository, mongo);
        when(repository.findByIdAndUserId("new", "user-1"))
                .thenReturn(java.util.Optional.of(address("new", false)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ShippingAddress.class))).thenThrow(new DuplicateKeyException("another default won"));

        assertThatThrownBy(() -> service.setDefault("user-1", "new"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void editingDetailsWithoutRequestingDefaultPreservesCurrentDefaultFlag() {
        AddressService service = new AddressService(repository, mongo);
        when(repository.findByIdAndUserId("old", "user-1"))
                .thenReturn(java.util.Optional.of(address("old", true)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ShippingAddress.class))).thenReturn(address("old", false));

        service.update("user-1", "old", request(false));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class),
                eq(ShippingAddress.class));
        assertThat(update.getValue().getUpdateObject().toString()).doesNotContain("isDefault");
        verify(mongo, never()).updateMulti(any(Query.class), any(Update.class), eq(ShippingAddress.class));
    }

    @Test
    void deletingDefaultPromotesAnotherAddressWithoutReinsertingIt() {
        AddressService service = new AddressService(repository, mongo);
        when(mongo.findAndRemove(any(Query.class), eq(ShippingAddress.class)))
                .thenReturn(address("old", true));
        when(repository.findByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(address("next", false)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ShippingAddress.class))).thenReturn(address("next", true));

        service.delete("user-1", "old");

        ArgumentCaptor<Query> promoteQuery = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(promoteQuery.capture(), any(Update.class),
                any(FindAndModifyOptions.class), eq(ShippingAddress.class));
        assertThat(promoteQuery.getValue().getQueryObject().toJson())
                .contains("next", "user-1", "isDefault");
        verify(repository, never()).save(any(ShippingAddress.class));
    }

    @Test
    void defaultAddressHasUniquePartialIndexPerUser() {
        CompoundIndex index = ShippingAddress.class.getAnnotation(CompoundIndex.class);

        assertThat(index.unique()).isTrue();
        assertThat(index.def()).contains("userId");
        assertThat(index.partialFilter()).contains("isDefault", "true");
    }

    private AddressRequest request(boolean isDefault) {
        return new AddressRequest(" Nguyễn An ", " 0908123456 ", " 12 Nguyễn Huệ ", "Bến Nghé",
                "Quận 1", "TP. Hồ Chí Minh", isDefault, "Nhà riêng");
    }

    private ShippingAddress address(String id, boolean isDefault) {
        Instant now = Instant.parse("2026-10-15T00:00:00Z");
        return new ShippingAddress(id, "user-1", "Nguyễn An", "0908123456", "12 Nguyễn Huệ",
                "Bến Nghé", "Quận 1", "TP. Hồ Chí Minh", isDefault, "Nhà riêng", now, now);
    }
}
