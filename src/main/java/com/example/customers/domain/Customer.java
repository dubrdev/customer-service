package com.example.customers.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() { }

    public Customer(String name, String cpf, String email, CustomerStatus status) {
        this.name = name;
        this.cpf = cpf;
        this.email = email;
        this.status = status;
    }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void update(String name, String cpf, String email, CustomerStatus status) {
        this.name = name;
        this.cpf = cpf;
        this.email = email;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCpf() { return cpf; }
    public String getEmail() { return email; }
    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
