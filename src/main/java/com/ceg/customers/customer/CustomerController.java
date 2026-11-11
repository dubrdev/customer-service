package com.ceg.customers.customer;

import com.ceg.customers.score.ScoreClient;
import com.ceg.customers.score.ScoreResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Porta de entrada da API: recebe as requisições HTTP e chama o serviço. */
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService service;
    private final ScoreClient scoreClient;

    public CustomerController(CustomerService service, ScoreClient scoreClient) {
        this.service = service;
        this.scoreClient = scoreClient;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return CustomerResponse.from(service.findById(id));
    }

    @GetMapping
    public List<CustomerResponse> list(@RequestParam(required = false) CustomerStatus status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size).stream().map(CustomerResponse::from).toList();
    }

    @GetMapping("/search")
    public List<CustomerResponse> search(@RequestParam String name,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.searchByName(name, page, size).stream().map(CustomerResponse::from).toList();
    }

    @GetMapping("/{id}/score")
    public ScoreResponse score(@PathVariable Long id) {
        Customer customer = service.findById(id);
        return scoreClient.fetchScore(customer.getCpf());
    }
}
