package com.neora.customer.address.service;

import com.neora.customer.address.dto.AddressRequest;
import com.neora.customer.address.dto.AddressResponse;
import com.neora.customer.address.model.ShippingAddress;
import com.neora.customer.address.repository.ShippingAddressRepository;
import com.neora.shared.error.ConflictException;
import com.neora.shared.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AddressService {
    private static final int MAX_ADDRESSES = 10;
    private final ShippingAddressRepository addresses;

    public AddressService(ShippingAddressRepository addresses) {
        this.addresses = addresses;
    }

    public List<AddressResponse> list(String userId) {
        return userAddresses(userId).stream().map(AddressResponse::from).toList();
    }

    public AddressResponse create(String userId, AddressRequest request) {
        if (addresses.countByUserId(userId) >= MAX_ADDRESSES) {
            throw new ConflictException("Mỗi tài khoản chỉ được lưu tối đa 10 địa chỉ");
        }
        boolean makeDefault = request.isDefault() || addresses.countByUserId(userId) == 0;
        if (makeDefault) clearDefault(userId, null);
        Instant now = Instant.now();
        ShippingAddress saved = addresses.save(new ShippingAddress(null, userId,
                clean(request.recipientName()), clean(request.phoneNumber()), clean(request.streetAddress()),
                cleanNullable(request.ward()), cleanNullable(request.district()), clean(request.city()),
                makeDefault, clean(request.label()), now, now));
        return AddressResponse.from(saved);
    }

    public AddressResponse update(String userId, String id, AddressRequest request) {
        ShippingAddress current = find(userId, id);
        if (request.isDefault()) clearDefault(userId, id);
        ShippingAddress saved = addresses.save(current.withDetails(clean(request.recipientName()),
                clean(request.phoneNumber()), clean(request.streetAddress()), cleanNullable(request.ward()),
                cleanNullable(request.district()), clean(request.city()),
                request.isDefault() || current.isDefault(), clean(request.label())));
        return AddressResponse.from(saved);
    }

    public void delete(String userId, String id) {
        ShippingAddress current = find(userId, id);
        addresses.delete(current);
        if (current.isDefault()) {
            userAddresses(userId).stream().findFirst()
                    .ifPresent(address -> addresses.save(address.withDefault(true)));
        }
    }

    public AddressResponse setDefault(String userId, String id) {
        ShippingAddress selected = find(userId, id);
        clearDefault(userId, id);
        return AddressResponse.from(addresses.save(selected.withDefault(true)));
    }

    private ShippingAddress find(String userId, String id) {
        return addresses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ"));
    }

    private List<ShippingAddress> userAddresses(String userId) {
        return addresses.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private void clearDefault(String userId, String exceptId) {
        List<ShippingAddress> changed = userAddresses(userId).stream()
                .filter(ShippingAddress::isDefault)
                .filter(address -> exceptId == null || !address.id().equals(exceptId))
                .map(address -> address.withDefault(false))
                .toList();
        if (!changed.isEmpty()) addresses.saveAll(changed);
    }

    private String clean(String value) {
        return value.trim();
    }

    private String cleanNullable(String value) {
        return value == null ? "" : value.trim();
    }
}
