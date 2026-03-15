package com.ecommerce.user.service;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.entity.Address;
import com.ecommerce.user.repository.AddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    public AddressResponse addAddress(Long userId, AddressRequest request) {
        if (request.isDefault()) {
            List<Address> existing = addressRepository.findByUserId(userId);
            existing.forEach(a -> a.setDefault(false));
            addressRepository.saveAll(existing);
        }

        Address address = Address.builder()
                .userId(userId)
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .phone(request.getPhone())
                .isDefault(request.isDefault())
                .build();

        Address saved = addressRepository.save(address);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long userId) {
        return addressRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private AddressResponse toResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .userId(address.getUserId())
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .country(address.getCountry())
                .phone(address.getPhone())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
