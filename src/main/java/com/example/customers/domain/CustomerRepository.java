package com.example.customers.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByCpf(String cpf);
    boolean existsByCpfAndIdNot(String cpf, Long id);
    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);
    Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
