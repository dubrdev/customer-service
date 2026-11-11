package com.ceg.customers.customer;

import com.ceg.customers.messaging.CustomerEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Regras de negócio do cadastro de clientes. */
@Service
public class CustomerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository repository;
    private final CustomerEventPublisher publisher;

    public CustomerService(CustomerRepository repository, CustomerEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public Customer create(CustomerRequest request) {
        if (repository.existsByCpf(request.cpf())) {
            throw cpfAlreadyRegistered();
        }
        CustomerStatus status = request.status() == null ? CustomerStatus.ACTIVE : request.status();
        Customer saved = repository.save(new Customer(request.name(), request.cpf(), request.email(), status));

        publisher.customerCreated(saved.getId());   // avisa o RabbitMQ que há um cliente novo
        return saved;
    }

    public Customer update(Long id, CustomerRequest request) {
        Customer customer = findById(id);
        if (repository.existsByCpfAndIdNot(request.cpf(), id)) {
            throw cpfAlreadyRegistered();
        }
        customer.setName(request.name());
        customer.setCpf(request.cpf());
        customer.setEmail(request.email());
        if (request.status() != null) {
            customer.setStatus(request.status());
        }
        return repository.save(customer);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw customerNotFound(id);
        }
        repository.deleteById(id);
    }

    public Customer findById(Long id) {
        return repository.findById(id).orElseThrow(() -> customerNotFound(id));
    }

    public List<Customer> list(CustomerStatus status, int page, int size) {
        Pageable pageable = pageable(page, size);
        return status == null
                ? repository.findAll(pageable).getContent()
                : repository.findByStatus(status, pageable);
    }

    public List<Customer> searchByName(String name, int page, int size) {
        return repository.findByNameContainingIgnoreCase(name, pageable(page, size));
    }

    /**
     * Troca o status de um cliente (usado pela mensagem do RabbitMQ).
     * Devolve false se o cliente não existe. Aplicar o mesmo status duas vezes dá o mesmo resultado,
     * então receber a mesma mensagem repetida não causa problema.
     */
    public boolean changeStatus(Long id, CustomerStatus status) {
        return repository.findById(id).map(customer -> {
            customer.setStatus(status);
            repository.save(customer);
            return true;
        }).orElse(false);
    }

    private Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id"));
    }

    private ResponseStatusException customerNotFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente " + id + " não encontrado");
    }

    private ResponseStatusException cpfAlreadyRegistered() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "CPF já cadastrado");
    }
}
