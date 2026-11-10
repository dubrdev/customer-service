package com.example.customers.messaging;

import java.time.Instant;
import java.util.UUID;

public record CustomerCreatedEvent(UUID eventId, String eventType, Long customerId, Instant createdAt) {
    public static final String TYPE = "CUSTOMER_CREATED";

    public static CustomerCreatedEvent of(Long customerId) {
        return new CustomerCreatedEvent(UUID.randomUUID(), TYPE, customerId, Instant.now());
    }
}
