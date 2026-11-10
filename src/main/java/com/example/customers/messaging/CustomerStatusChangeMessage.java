package com.example.customers.messaging;

import com.example.customers.domain.CustomerStatus;
import java.util.UUID;

public record CustomerStatusChangeMessage(UUID eventId, String eventType, Long customerId, CustomerStatus status) {
    public static final String TYPE = "CUSTOMER_STATUS_CHANGE";
}
