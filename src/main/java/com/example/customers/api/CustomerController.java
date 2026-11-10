package com.example.customers.api;

import com.example.customers.domain.CustomerStatus;
import com.example.customers.service.CustomerService;
import com.example.customers.service.ScoreService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customers;
    private final ScoreService scores;

    public CustomerController(CustomerService customers, ScoreService scores) {
        this.customers = customers;
        this.scores = scores;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse body = CustomerResponse.from(customers.create(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(customers.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customers.delete(id);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return CustomerResponse.from(customers.findById(id));
    }

    @GetMapping
    public Page<CustomerResponse> list(@RequestParam(required = false) CustomerStatus status,
                                       @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return customers.list(status, pageable).map(CustomerResponse::from);
    }

    @GetMapping("/search")
    public Page<CustomerResponse> search(@RequestParam String name,
                                         @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return customers.searchByName(name, pageable).map(CustomerResponse::from);
    }

    @GetMapping("/{id}/score")
    public ScoreResponse score(@PathVariable Long id) {
        return scores.getScore(id);
    }
}
