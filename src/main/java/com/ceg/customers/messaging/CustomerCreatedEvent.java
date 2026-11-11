package com.ceg.customers.messaging;

import java.time.Instant;

/** Mensagem publicada quando um cliente é cadastrado. */
public record CustomerCreatedEvent(String eventType, Long customerId, Instant createdAt) {

    public static final String TYPE = "CUSTOMER_CREATED";

    public static CustomerCreatedEvent of(Long customerId) {
        return new CustomerCreatedEvent(TYPE, customerId, Instant.now());
    }
}
