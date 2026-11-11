package com.ceg.customers.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publica no RabbitMQ os eventos dos clientes. */
@Component
public class CustomerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CustomerEventPublisher.class);

    private final RabbitTemplate rabbit;

    public CustomerEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void customerCreated(Long customerId) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.CREATED_ROUTING_KEY,
                    CustomerCreatedEvent.of(customerId));
        } catch (AmqpException e) {
            // O cliente já foi salvo, então o cadastro não deve falhar por causa do RabbitMQ.
            log.error("Não foi possível publicar o evento do cliente {}", customerId, e);
        }
    }
}
