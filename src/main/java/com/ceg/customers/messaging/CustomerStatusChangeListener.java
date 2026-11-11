package com.ceg.customers.messaging;

import com.ceg.customers.customer.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Fica escutando a fila de mudança de status e atualiza o cliente. */
@Component
public class CustomerStatusChangeListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerStatusChangeListener.class);

    private final CustomerService service;

    public CustomerStatusChangeListener(CustomerService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitConfig.STATUS_QUEUE)
    public void onMessage(CustomerStatusChangeMessage message) {
        if (message.customerId() == null || message.status() == null) {
            log.warn("Mensagem inválida ignorada: {}", message);
            return;
        }
        boolean updated = service.changeStatus(message.customerId(), message.status());
        if (updated) {
            log.info("Cliente {} agora está {}", message.customerId(), message.status());
        } else {
            log.warn("Cliente {} não existe; mensagem ignorada", message.customerId());
        }
    }
}
