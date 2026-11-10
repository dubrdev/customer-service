package com.example.customers.api;

import com.example.customers.domain.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "must contain exactly 11 digits") String cpf,
        @NotBlank @Email @Size(max = 254) String email,
        CustomerStatus status) { }
