package com.ecommerce.user.service;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.entity.Address;
import com.ecommerce.user.repository.AddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AddressService.
 * Validates: Requirements 2
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    @Test
    void addAddress_withDefault_unsetsExistingDefaults() {
        Address existingAddress = Address.builder()
                .id(1L)
                .userId(10L)
                .street("Old Street")
                .city("Old City")
                .state("OS")
                .zipCode("00000")
                .country("US")
                .isDefault(true)
                .build();

        Address newAddress = Address.builder()
                .id(2L)
                .userId(10L)
                .street("New Street")
                .city("New City")
                .state("NS")
                .zipCode("11111")
                .country("US")
                .isDefault(true)
                .build();

        when(addressRepository.findByUserId(10L)).thenReturn(List.of(existingAddress));
        when(addressRepository.saveAll(anyList())).thenReturn(List.of());
        when(addressRepository.save(any(Address.class))).thenReturn(newAddress);

        AddressRequest request = new AddressRequest(
                "New Street", "New City", "NS", "11111", "US", "+1234567890", true
        );

        addressService.addAddress(10L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Address>> captor = ArgumentCaptor.forClass(List.class);
        verify(addressRepository).saveAll(captor.capture());

        List<Address> savedAddresses = captor.getValue();
        assertThat(savedAddresses).allMatch(a -> !a.isDefault());
    }

    @Test
    void addAddress_notDefault_doesNotUnsetExisting() {
        Address newAddress = Address.builder()
                .id(2L)
                .userId(10L)
                .street("New Street")
                .city("New City")
                .state("NS")
                .zipCode("11111")
                .country("US")
                .isDefault(false)
                .build();

        when(addressRepository.save(any(Address.class))).thenReturn(newAddress);

        AddressRequest request = new AddressRequest(
                "New Street", "New City", "NS", "11111", "US", "+1234567890", false
        );

        addressService.addAddress(10L, request);

        verify(addressRepository, never()).saveAll(anyList());
    }

    @Test
    void getAddresses_returnsAllAddresses() {
        Address addr1 = Address.builder().id(1L).userId(10L).street("Street 1").city("City").state("ST").zipCode("00001").country("US").build();
        Address addr2 = Address.builder().id(2L).userId(10L).street("Street 2").city("City").state("ST").zipCode("00002").country("US").build();

        when(addressRepository.findByUserId(10L)).thenReturn(List.of(addr1, addr2));

        List<AddressResponse> result = addressService.getAddresses(10L);

        assertThat(result).hasSize(2);
    }
}
