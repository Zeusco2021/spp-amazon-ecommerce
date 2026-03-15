package com.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

    @NotBlank
    @Size(max = 255, message = "Street must not exceed 255 characters")
    private String street;

    @NotBlank
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotBlank
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    @NotBlank
    @Size(max = 20, message = "Zip code must not exceed 20 characters")
    private String zipCode;

    @NotBlank
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone format")
    private String phone;

    private boolean isDefault;
}
