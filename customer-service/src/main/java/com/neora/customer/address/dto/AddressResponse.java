package com.neora.customer.address.dto;

import com.neora.customer.address.model.ShippingAddress;

import java.time.Instant;

public record AddressResponse(
        String id,
        String recipientName,
        String phoneNumber,
        String streetAddress,
        String ward,
        String district,
        String city,
        boolean isDefault,
        String label,
        Instant createdAt,
        Instant updatedAt
) {
    public static AddressResponse from(ShippingAddress address) {
        return new AddressResponse(address.id(), address.recipientName(), address.phoneNumber(),
                address.streetAddress(), address.ward(), address.district(), address.city(), address.isDefault(),
                address.label(), address.createdAt(), address.updatedAt());
    }
}
