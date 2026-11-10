package com.example.customers.api;

import com.example.customers.domain.Customer;
import com.example.customers.domain.CustomerStatus;

public record CustomerResponse(Long id, String name, String cpf, String email, CustomerStatus status) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getCpf(), c.getEmail(), c.getStatus());
    }
}
