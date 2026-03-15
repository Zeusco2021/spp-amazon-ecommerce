package com.ecommerce.user.controller;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping
    public ResponseEntity<AddressResponse> addAddress(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.addAddress(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> getAddresses(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        List<AddressResponse> addresses = addressService.getAddresses(userId);
        return ResponseEntity.ok(addresses);
    }
}
