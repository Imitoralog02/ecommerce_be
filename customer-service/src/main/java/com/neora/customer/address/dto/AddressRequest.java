package com.neora.customer.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 100) String recipientName,
        @NotBlank @Pattern(regexp = "^[0-9+ .()-]{8,20}$") String phoneNumber,
        @NotBlank @Size(max = 200) String streetAddress,
        @Size(max = 100) String ward,
        @Size(max = 100) String district,
        @NotBlank @Size(max = 100) String city,
        boolean isDefault,
        @NotBlank @Size(max = 30) String label
) {}
