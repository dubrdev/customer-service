package com.ceg.customers.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cria no RabbitMQ (se ainda não existirem) uma exchange e duas filas:
 *
 *   customer.events  --customer.created-->       customer.created.queue          (nós publicamos aqui)
 *   customer.events  --customer.status.change--> customer.status-change.queue    (nós consumimos daqui)
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "customer.events";
    public static final String CREATED_ROUTING_KEY = "customer.created";
    public static final String STATUS_ROUTING_KEY = "customer.status.change";
    public static final String CREATED_QUEUE = "customer.created.queue";
    public static final String STATUS_QUEUE = "customer.status-change.queue";

    @Bean
    TopicExchange customerEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue createdQueue() {
        return new Queue(CREATED_QUEUE);
    }

    @Bean
    Queue statusChangeQueue() {
        return new Queue(STATUS_QUEUE);
    }

    @Bean
    Binding createdBinding() {
        return BindingBuilder.bind(createdQueue()).to(customerEventsExchange()).with(CREATED_ROUTING_KEY);
    }

    @Bean
    Binding statusChangeBinding() {
        return BindingBuilder.bind(statusChangeQueue()).to(customerEventsExchange()).with(STATUS_ROUTING_KEY);
    }

    /**
     * Mensagens em JSON. O tipo é deduzido pelo parâmetro do método que recebe a mensagem,
     * então quem publica (ex.: o painel do RabbitMQ) não precisa mandar cabeçalhos especiais.
     */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
