package com.ceg.customers.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados que chegam no corpo do POST/PUT. Se o status não for informado, o cliente fica ACTIVE. */
public record CustomerRequest(
        @NotBlank @Size(max = 150)
        String name,

        @NotBlank @Pattern(regexp = "\\d{11}", message = "deve ter exatamente 11 dígitos")
        String cpf,

        @NotBlank @Email @Size(max = 254)
        String email,

        CustomerStatus status) { }
