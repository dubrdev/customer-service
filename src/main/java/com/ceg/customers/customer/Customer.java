package com.ceg.customers.customer;

import jakarta.persistence.*;

/** Um cliente cadastrado. Cada objeto vira uma linha da tabela "customers". */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 11)   // CPF não pode repetir
    private String cpf;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    protected Customer() { }   // exigido pelo JPA

    public Customer(String name, String cpf, String email, CustomerStatus status) {
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

    public void setName(String name) { this.name = name; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public void setEmail(String email) { this.email = email; }
    public void setStatus(CustomerStatus status) { this.status = status; }
}
