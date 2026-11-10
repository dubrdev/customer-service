package com.example.customers.service;

import com.example.customers.domain.*;
import com.example.customers.messaging.CustomerStatusChangeMessage;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer: the eventId insert and the status update share ONE transaction.
 * - duplicate delivery    -> eventId already stored -> ignored;
 * - two concurrent copies -> PK violation rolls one back; its retry finds the id and is ignored;
 * - failure before commit -> nothing is persisted, so the retry processes it again safely.
 */
@Service
public class StatusChangeProcessor {

    public enum Outcome { PROCESSED, DUPLICATE }

    private static final Logger log = LoggerFactory.getLogger(StatusChangeProcessor.class);

    private final CustomerRepository customers;
    private final ProcessedEventRepository processed;
    private final MeterRegistry meters;

    public StatusChangeProcessor(CustomerRepository customers, ProcessedEventRepository processed, MeterRegistry meters) {
        this.customers = customers;
        this.processed = processed;
        this.meters = meters;
    }

    @Transactional
    public Outcome process(CustomerStatusChangeMessage m) {
        if (m.eventId() == null || m.customerId() == null || m.status() == null
                || !CustomerStatusChangeMessage.TYPE.equals(m.eventType())) {
            throw new AmqpRejectAndDontRequeueException("Invalid status change message");
        }
        if (processed.existsById(m.eventId())) {
            log.info("Duplicate event {} ignored", m.eventId());
            meters.counter("customer.status_change.events", "result", "duplicate").increment();
            return Outcome.DUPLICATE;
        }
        Customer customer = customers.findById(m.customerId()).orElseThrow(
                () -> new AmqpRejectAndDontRequeueException("Customer " + m.customerId() + " not found"));
        customer.setStatus(m.status());
        processed.save(new ProcessedEvent(m.eventId()));
        meters.counter("customer.status_change.events", "result", "processed").increment();
        return Outcome.PROCESSED;
    }
}
