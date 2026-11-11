package com.ceg.customers.messaging;

import com.ceg.customers.customer.CustomerStatus;

/** Mensagem recebida pedindo para mudar o status de um cliente. */
public record CustomerStatusChangeMessage(String eventType, Long customerId, CustomerStatus status) { }
