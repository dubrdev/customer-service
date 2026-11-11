package com.ceg.customers.messaging;

import com.ceg.customers.customer.CustomerService;
import com.ceg.customers.customer.CustomerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerStatusChangeListenerTest {

    @Mock CustomerService service;
    @InjectMocks CustomerStatusChangeListener listener;

    @Test
    void mensagemValidaMudaOStatusDoCliente() {
        when(service.changeStatus(123L, CustomerStatus.INACTIVE)).thenReturn(true);

        listener.onMessage(new CustomerStatusChangeMessage("CUSTOMER_STATUS_CHANGE", 123L, CustomerStatus.INACTIVE));

        verify(service).changeStatus(123L, CustomerStatus.INACTIVE);
    }

    @Test
    void mensagemSemStatusEIgnorada() {
        listener.onMessage(new CustomerStatusChangeMessage("CUSTOMER_STATUS_CHANGE", 123L, null));

        verify(service, never()).changeStatus(any(), any());
    }

    @Test
    void mensagemSemIdDoClienteEIgnorada() {
        listener.onMessage(new CustomerStatusChangeMessage("CUSTOMER_STATUS_CHANGE", null, CustomerStatus.BLOCKED));

        verify(service, never()).changeStatus(any(), any());
    }

    @Test
    void clienteInexistenteNaoGeraErro() {
        when(service.changeStatus(999L, CustomerStatus.ACTIVE)).thenReturn(false);

        listener.onMessage(new CustomerStatusChangeMessage("CUSTOMER_STATUS_CHANGE", 999L, CustomerStatus.ACTIVE));

        verify(service).changeStatus(999L, CustomerStatus.ACTIVE);
    }
}
