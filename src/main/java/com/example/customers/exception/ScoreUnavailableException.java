package com.example.customers.exception;

/** External score provider failed (5xx, timeout, connection refused, invalid body). */
public class ScoreUnavailableException extends RuntimeException {
    public ScoreUnavailableException(String message, Throwable cause) { super(message, cause); }
    public ScoreUnavailableException(String message) { super(message); }
}
