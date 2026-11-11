package com.ceg.customers.customer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** O Spring Data gera as consultas a partir do nome dos métodos. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    List<Customer> findByStatus(CustomerStatus status, Pageable pageable);

    List<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
