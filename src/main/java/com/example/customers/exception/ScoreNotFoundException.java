package com.example.customers.exception;

public class ScoreNotFoundException extends RuntimeException {
    public ScoreNotFoundException() { super("No score found for this customer"); }
}
