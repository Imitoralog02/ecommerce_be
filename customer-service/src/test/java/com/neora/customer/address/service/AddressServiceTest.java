package com.neora.customer.address.service;

import com.neora.customer.address.dto.AddressRequest;
import com.neora.customer.address.model.ShippingAddress;
import com.neora.customer.address.repository.ShippingAddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    @Mock ShippingAddressRepository repository;

    @Test
    void firstAddressBecomesDefaultEvenWhenRequestDoesNotAskForIt() {
        AddressService service = new AddressService(repository);
        when(repository.countByUserId("user-1")).thenReturn(0L);
        when(repository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            ShippingAddress value = invocation.getArgument(0);
            return new ShippingAddress("address-1", value.userId(), value.recipientName(), value.phoneNumber(),
                    value.streetAddress(), value.ward(), value.district(), value.city(), value.isDefault(),
                    value.label(), value.createdAt(), value.updatedAt());
        });

        var response = service.create("user-1", request(false));

        assertThat(response.isDefault()).isTrue();
        assertThat(response.recipientName()).isEqualTo("Nguyễn An");
    }

    @Test
    void settingDefaultClearsPreviousDefaultForSameUser() {
        AddressService service = new AddressService(repository);
        ShippingAddress oldDefault = address("old", true);
        ShippingAddress selected = address("new", false);
        when(repository.findByIdAndUserId("new", "user-1")).thenReturn(java.util.Optional.of(selected));
        when(repository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(selected, oldDefault));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.setDefault("user-1", "new");

        assertThat(response.isDefault()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShippingAddress>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(address -> {
            assertThat(address.id()).isEqualTo("old");
            assertThat(address.isDefault()).isFalse();
        });
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
