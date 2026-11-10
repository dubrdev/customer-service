package com.example.customers.messaging;

import com.example.customers.config.RabbitConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CustomerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventPublisher.class);

    private final RabbitTemplate rabbit;
    private final MeterRegistry meters;

    public CustomerEventPublisher(RabbitTemplate rabbit, MeterRegistry meters) {
        this.rabbit = rabbit;
        this.meters = meters;
    }

    /** Runs only if the DB commit succeeded, so we never publish an event for a customer that was rolled back. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CustomerCreatedEvent event) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.CREATED_ROUTING_KEY, event);
            meters.counter("customer.events.published", "result", "ok").increment();
        } catch (AmqpException e) {
            // The customer is already saved; the API call must not fail. Alert on this metric.
            // Production hardening: transactional outbox table + relay job (see README, "Next steps").
            meters.counter("customer.events.published", "result", "failed").increment();
            log.error("Failed to publish {} for customer {}", event.eventType(), event.customerId(), e);
        }
    }
}
