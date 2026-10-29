package com.neora.customer.address.service;

import com.neora.customer.address.dto.AddressRequest;
import com.neora.customer.address.dto.AddressResponse;
import com.neora.customer.address.model.ShippingAddress;
import com.neora.customer.address.repository.ShippingAddressRepository;
import com.neora.shared.error.ConflictException;
import com.neora.shared.error.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AddressService {
    private static final int MAX_ADDRESSES = 10;
    private final ShippingAddressRepository addresses;
    private final MongoTemplate mongo;

    public AddressService(ShippingAddressRepository addresses, MongoTemplate mongo) {
        this.addresses = addresses;
        this.mongo = mongo;
    }

    public List<AddressResponse> list(String userId) {
        return userAddresses(userId).stream().map(AddressResponse::from).toList();
    }

    public AddressResponse create(String userId, AddressRequest request) {
        long count = addresses.countByUserId(userId);
        if (count >= MAX_ADDRESSES) {
            throw new ConflictException("Mỗi tài khoản chỉ được lưu tối đa 10 địa chỉ");
        }
        boolean makeDefault = request.isDefault() || count == 0;
        if (makeDefault) clearDefault(userId, null);
        Instant now = Instant.now();
        ShippingAddress newAddress = new ShippingAddress(null, userId,
                clean(request.recipientName()), clean(request.phoneNumber()), clean(request.streetAddress()),
                cleanNullable(request.ward()), cleanNullable(request.district()), clean(request.city()),
                makeDefault, clean(request.label()), now, now);
        try {
            return AddressResponse.from(addresses.insert(newAddress));
        } catch (DuplicateKeyException duplicate) {
            if (makeDefault && !request.isDefault()) {
                // Two first-address requests may race. Keep the winning default and save this one as ordinary.
                return AddressResponse.from(addresses.insert(newAddress.withDefault(false)));
            }
            throw new ConflictException("Địa chỉ mặc định vừa thay đổi; vui lòng thử lại");
        }
    }

    public AddressResponse update(String userId, String id, AddressRequest request) {
        find(userId, id);
        if (request.isDefault()) clearDefault(userId, id);
        Update update = new Update().set("recipientName", clean(request.recipientName()))
                .set("phoneNumber", clean(request.phoneNumber()))
                .set("streetAddress", clean(request.streetAddress()))
                .set("ward", cleanNullable(request.ward()))
                .set("district", cleanNullable(request.district()))
                .set("city", clean(request.city()))
                .set("label", clean(request.label()))
                .set("updatedAt", Instant.now());
        if (request.isDefault()) update.set("isDefault", true);
        try {
            return AddressResponse.from(modify(userId, id, update));
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException("Địa chỉ mặc định vừa thay đổi; vui lòng thử lại");
        }
    }

    public void delete(String userId, String id) {
        ShippingAddress deleted = mongo.findAndRemove(owned(userId, id), ShippingAddress.class);
        if (deleted == null) throw new NotFoundException("Không tìm thấy địa chỉ");
        if (deleted.isDefault()) {
            for (ShippingAddress candidate : userAddresses(userId)) {
                try {
                    ShippingAddress promoted = mongo.findAndModify(owned(userId, candidate.id())
                                    .addCriteria(Criteria.where("isDefault").is(false)),
                            new Update().set("isDefault", true).set("updatedAt", Instant.now()),
                            FindAndModifyOptions.options().returnNew(true), ShippingAddress.class);
                    if (promoted != null) break;
                } catch (DuplicateKeyException anotherDefaultWon) {
                    break;
                }
            }
        }
    }

    public AddressResponse setDefault(String userId, String id) {
        find(userId, id);
        clearDefault(userId, id);
        try {
            return AddressResponse.from(modify(userId, id,
                    new Update().set("isDefault", true).set("updatedAt", Instant.now())));
        } catch (DuplicateKeyException duplicate) {
            throw new ConflictException("Địa chỉ mặc định vừa thay đổi; vui lòng thử lại");
        }
    }

    private ShippingAddress find(String userId, String id) {
        return addresses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ"));
    }

    private List<ShippingAddress> userAddresses(String userId) {
        return addresses.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private void clearDefault(String userId, String exceptId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("isDefault").is(true));
        if (exceptId != null) query.addCriteria(Criteria.where("_id").ne(exceptId));
        mongo.updateMulti(query, new Update().set("isDefault", false).set("updatedAt", Instant.now()),
                ShippingAddress.class);
    }

    private ShippingAddress modify(String userId, String id, Update update) {
        ShippingAddress updated = mongo.findAndModify(owned(userId, id), update,
                FindAndModifyOptions.options().returnNew(true), ShippingAddress.class);
        if (updated == null) throw new NotFoundException("Không tìm thấy địa chỉ");
        return updated;
    }

    private Query owned(String userId, String id) {
        return Query.query(Criteria.where("_id").is(id).and("userId").is(userId));
    }

    private String clean(String value) {
        return value.trim();
    }

    private String cleanNullable(String value) {
        return value == null ? "" : value.trim();
    }
}
