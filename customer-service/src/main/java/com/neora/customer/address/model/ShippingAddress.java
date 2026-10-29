package com.neora.customer.address.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("shipping_addresses")
@CompoundIndex(name = "unique_default_address_per_user", def = "{'userId': 1}",
        unique = true, partialFilter = "{'isDefault': true}")
public record ShippingAddress(
        @Id String id,
        @Indexed String userId,
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
    public ShippingAddress withDetails(String name, String phone, String street, String newWard,
                                       String newDistrict, String newCity, boolean defaultAddress,
                                       String newLabel) {
        return new ShippingAddress(id, userId, name, phone, street, newWard, newDistrict, newCity,
                defaultAddress, newLabel, createdAt, Instant.now());
    }

    public ShippingAddress withDefault(boolean defaultAddress) {
        return new ShippingAddress(id, userId, recipientName, phoneNumber, streetAddress, ward, district, city,
                defaultAddress, label, createdAt, Instant.now());
    }
}
