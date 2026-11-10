package com.example.customers.service;

import com.example.customers.domain.*;
import com.example.customers.messaging.CustomerStatusChangeMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusChangeProcessorTest {

    @Mock CustomerRepository customers;
    @Mock ProcessedEventRepository processed;
    SimpleMeterRegistry meters;
    StatusChangeProcessor processor;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        processor = new StatusChangeProcessor(customers, processed, meters);
    }

    private CustomerStatusChangeMessage message(UUID id) {
        return new CustomerStatusChangeMessage(id, "CUSTOMER_STATUS_CHANGE", 123L, CustomerStatus.INACTIVE);
    }

    @Test
    void newEvent_updatesStatusAndStoresEventId() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer("João", "12345678901", "j@e.com", CustomerStatus.ACTIVE);
        when(processed.existsById(id)).thenReturn(false);
        when(customers.findById(123L)).thenReturn(Optional.of(customer));

        var outcome = processor.process(message(id));

        assertThat(outcome).isEqualTo(StatusChangeProcessor.Outcome.PROCESSED);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        verify(processed).save(any(ProcessedEvent.class));
        assertThat(meters.counter("customer.status_change.events", "result", "processed").count()).isEqualTo(1.0);
    }

    @Test
    void duplicateEvent_isIgnored() {
        UUID id = UUID.randomUUID();
        when(processed.existsById(id)).thenReturn(true);

        var outcome = processor.process(message(id));

        assertThat(outcome).isEqualTo(StatusChangeProcessor.Outcome.DUPLICATE);
        verify(customers, never()).findById(any());
        verify(processed, never()).save(any());
    }

    @Test
    void unknownCustomer_isRejectedWithoutRequeue() {
        UUID id = UUID.randomUUID();
        when(processed.existsById(id)).thenReturn(false);
        when(customers.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(message(id)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(processed, never()).save(any());
    }

    @Test
    void invalidMessage_isRejectedWithoutRequeue() {
        var invalid = new CustomerStatusChangeMessage(null, "CUSTOMER_STATUS_CHANGE", 1L, CustomerStatus.ACTIVE);
        assertThatThrownBy(() -> processor.process(invalid)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        var wrongType = new CustomerStatusChangeMessage(UUID.randomUUID(), "SOMETHING_ELSE", 1L, CustomerStatus.ACTIVE);
        assertThatThrownBy(() -> processor.process(wrongType)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
