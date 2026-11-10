package com.example.customers.exception;

public class DuplicateCpfException extends RuntimeException {
    public DuplicateCpfException() { super("CPF already registered"); }
}
