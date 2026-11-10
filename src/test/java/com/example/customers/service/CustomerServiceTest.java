package com.example.customers.service;

import com.example.customers.api.CustomerRequest;
import com.example.customers.domain.Customer;
import com.example.customers.domain.CustomerRepository;
import com.example.customers.domain.CustomerStatus;
import com.example.customers.exception.CustomerNotFoundException;
import com.example.customers.exception.DuplicateCpfException;
import com.example.customers.messaging.CustomerCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository repository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks CustomerService service;

    private final CustomerRequest request = new CustomerRequest("João da Silva", "12345678901", "joao@email.com", null);

    @Test
    void create_savesWithActiveStatusAndPublishesEvent() {
        when(repository.existsByCpf("12345678901")).thenReturn(false);
        when(repository.saveAndFlush(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 1L);
            return c;
        });

        Customer saved = service.create(request);

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        ArgumentCaptor<CustomerCreatedEvent> captor = ArgumentCaptor.forClass(CustomerCreatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().customerId()).isEqualTo(1L);
        assertThat(captor.getValue().eventType()).isEqualTo("CUSTOMER_CREATED");
        assertThat(captor.getValue().eventId()).isNotNull();
    }

    @Test
    void create_rejectsDuplicateCpf_withoutSavingOrPublishing() {
        when(repository.existsByCpf("12345678901")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(DuplicateCpfException.class);

        verify(repository, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void update_unknownCustomer_throwsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, request)).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void update_cpfBelongingToAnotherCustomer_throwsDuplicate() {
        Customer existing = new Customer("Maria", "11111111111", "maria@email.com", CustomerStatus.ACTIVE);
        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        when(repository.existsByCpfAndIdNot("12345678901", 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(2L, request)).isInstanceOf(DuplicateCpfException.class);
    }

    @Test
    void update_keepsCurrentStatusWhenNotProvided() {
        Customer existing = new Customer("Maria", "11111111111", "maria@email.com", CustomerStatus.BLOCKED);
        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        when(repository.existsByCpfAndIdNot(any(), any())).thenReturn(false);
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        Customer updated = service.update(2L, request);

        assertThat(updated.getName()).isEqualTo("João da Silva");
        assertThat(updated.getStatus()).isEqualTo(CustomerStatus.BLOCKED);
    }

    @Test
    void delete_unknownCustomer_throwsNotFound() {
        when(repository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(CustomerNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }
}
