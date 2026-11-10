package com.example.customers.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Long id) { super("Customer " + id + " not found"); }
}
