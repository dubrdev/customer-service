package com.example.customers.api;

import com.example.customers.exception.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(CustomerNotFoundException e) { return problem(HttpStatus.NOT_FOUND, e.getMessage(), null); }

    @ExceptionHandler(ScoreNotFoundException.class)
    ResponseEntity<ProblemDetail> scoreNotFound(ScoreNotFoundException e) { return problem(HttpStatus.NOT_FOUND, e.getMessage(), null); }

    @ExceptionHandler(DuplicateCpfException.class)
    ResponseEntity<ProblemDetail> duplicate(DuplicateCpfException e) { return problem(HttpStatus.CONFLICT, e.getMessage(), null); }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> conflict(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification, retry the request", null);
    }

    @ExceptionHandler({ScoreUnavailableException.class, CallNotPermittedException.class})
    ResponseEntity<ProblemDetail> scoreUnavailable(Exception e) {
        log.warn("Score provider unavailable: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Score service is temporarily unavailable", "10");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", null);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, String retryAfter) {
        var builder = ResponseEntity.status(status);
        if (retryAfter != null) builder.header(HttpHeaders.RETRY_AFTER, retryAfter);
        return builder.body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
