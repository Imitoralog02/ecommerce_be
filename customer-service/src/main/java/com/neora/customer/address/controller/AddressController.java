package com.neora.customer.address.controller;

import com.neora.customer.address.dto.AddressRequest;
import com.neora.customer.address.dto.AddressResponse;
import com.neora.customer.address.service.AddressService;
import com.neora.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {
    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return addresses.list(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody AddressRequest request) {
        return addresses.create(user.id(), request);
    }

    @PutMapping("/{id}")
    public AddressResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
                                  @Valid @RequestBody AddressRequest request) {
        return addresses.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        addresses.delete(user.id(), id);
    }

    @PatchMapping("/{id}/default")
    public AddressResponse setDefault(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return addresses.setDefault(user.id(), id);
    }
}
