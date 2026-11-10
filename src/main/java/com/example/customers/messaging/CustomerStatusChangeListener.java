package com.example.customers.messaging;

import com.example.customers.config.RabbitConfig;
import com.example.customers.service.StatusChangeProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CustomerStatusChangeListener {

    private final StatusChangeProcessor processor;

    public CustomerStatusChangeListener(StatusChangeProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = RabbitConfig.STATUS_QUEUE)
    public void onMessage(CustomerStatusChangeMessage message) {
        processor.process(message);
    }
}
