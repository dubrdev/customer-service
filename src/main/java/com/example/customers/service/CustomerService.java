package com.example.customers.service;

import com.example.customers.api.CustomerRequest;
import com.example.customers.domain.*;
import com.example.customers.exception.CustomerNotFoundException;
import com.example.customers.exception.DuplicateCpfException;
import com.example.customers.messaging.CustomerCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final ApplicationEventPublisher events;

    public CustomerService(CustomerRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public Customer create(CustomerRequest r) {
        if (repository.existsByCpf(r.cpf())) throw new DuplicateCpfException();
        Customer c = new Customer(r.name(), r.cpf(), r.email(), r.status() == null ? CustomerStatus.ACTIVE : r.status());
        try {
            c = repository.saveAndFlush(c);          // unique constraint protects against concurrent requests
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCpfException();
        }
        // Published to RabbitMQ only AFTER the transaction commits (see CustomerEventPublisher).
        events.publishEvent(CustomerCreatedEvent.of(c.getId()));
        return c;
    }

    @Transactional
    public Customer update(Long id, CustomerRequest r) {
        Customer c = findById(id);
        if (repository.existsByCpfAndIdNot(r.cpf(), id)) throw new DuplicateCpfException();
        c.update(r.name(), r.cpf(), r.email(), r.status() == null ? c.getStatus() : r.status());
        try {
            return repository.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCpfException();
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new CustomerNotFoundException(id);
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Customer findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Customer> list(CustomerStatus status, Pageable pageable) {
        return status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Customer> searchByName(String name, Pageable pageable) {
        return repository.findByNameContainingIgnoreCase(name, pageable);
    }
}
