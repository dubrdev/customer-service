package com.example.customers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "customer.events";
    public static final String DLX = "customer.events.dlx";
    public static final String CREATED_ROUTING_KEY = "customer.created";
    public static final String STATUS_ROUTING_KEY = "customer.status.change";
    public static final String CREATED_QUEUE = "customer.created.queue";
    public static final String STATUS_QUEUE = "customer.status-change.queue";
    public static final String STATUS_DLQ = "customer.status-change.dlq";

    @Bean TopicExchange customerEventsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean Queue createdQueue() { return QueueBuilder.durable(CREATED_QUEUE).build(); }

    @Bean Queue statusChangeQueue() {
        return QueueBuilder.durable(STATUS_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(STATUS_DLQ).build();
    }

    @Bean Queue statusChangeDlq() { return QueueBuilder.durable(STATUS_DLQ).build(); }

    @Bean Binding createdBinding() {
        return BindingBuilder.bind(createdQueue()).to(customerEventsExchange()).with(CREATED_ROUTING_KEY);
    }

    @Bean Binding statusBinding() {
        return BindingBuilder.bind(statusChangeQueue()).to(customerEventsExchange()).with(STATUS_ROUTING_KEY);
    }

    @Bean Binding dlqBinding() {
        return BindingBuilder.bind(statusChangeDlq()).to(deadLetterExchange()).with(STATUS_DLQ);
    }

    /** JSON converter; the payload type is inferred from the listener method, so external producers need no __TypeId__ header. */
    @Bean MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
