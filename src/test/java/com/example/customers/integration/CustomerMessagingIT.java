package com.example.customers.integration;

import com.example.customers.config.RabbitConfig;
import com.example.customers.domain.Customer;
import com.example.customers.domain.CustomerRepository;
import com.example.customers.domain.CustomerStatus;
import com.example.customers.domain.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerMessagingIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired ObjectMapper objectMapper;
    @Autowired CustomerRepository customers;
    @Autowired ProcessedEventRepository processedEvents;

    @BeforeEach
    void clean() {
        customers.deleteAll();
        processedEvents.deleteAll();
        while (rabbit.receive(RabbitConfig.CREATED_QUEUE) != null) { /* drain */ }
        while (rabbit.receive(RabbitConfig.STATUS_DLQ) != null) { /* drain */ }
    }

    private void send(UUID eventId, Long customerId, String status) {
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.STATUS_ROUTING_KEY, Map.of(
                "eventId", eventId.toString(),
                "eventType", "CUSTOMER_STATUS_CHANGE",
                "customerId", customerId,
                "status", status));
    }

    private Customer newCustomer(String cpf) {
        return customers.save(new Customer("Msg " + cpf, cpf, cpf + "@email.com", CustomerStatus.ACTIVE));
    }

    @Test
    void creatingCustomer_publishesCustomerCreatedEvent() throws Exception {
        mvc.perform(post("/customers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Evento\",\"cpf\":\"30000000001\",\"email\":\"e@email.com\"}"))
                .andExpect(status().isCreated());

        Message message = rabbit.receive(RabbitConfig.CREATED_QUEUE, 5000);

        assertThat(message).isNotNull();
        JsonNode json = objectMapper.readTree(message.getBody());
        assertThat(json.get("eventType").asText()).isEqualTo("CUSTOMER_CREATED");
        assertThat(json.get("customerId").asLong()).isEqualTo(customers.findAll().getFirst().getId());
        assertThat(UUID.fromString(json.get("eventId").asText())).isNotNull();
        assertThat(json.get("createdAt").asText()).isNotBlank();
    }

    @Test
    void statusChangeMessage_updatesCustomer() {
        Customer c = newCustomer("40000000001");

        send(UUID.randomUUID(), c.getId(), "INACTIVE");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(customers.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CustomerStatus.INACTIVE));
    }

    @Test
    void duplicateMessage_isProcessedOnlyOnce() {
        Customer first = newCustomer("40000000002");
        Customer second = newCustomer("40000000003");
        UUID duplicatedId = UUID.randomUUID();

        send(duplicatedId, first.getId(), "INACTIVE");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(customers.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(CustomerStatus.INACTIVE));

        // someone re-activates the customer; the same message is delivered again
        Customer reactivated = customers.findById(first.getId()).orElseThrow();
        reactivated.setStatus(CustomerStatus.ACTIVE);
        customers.save(reactivated);
        send(duplicatedId, first.getId(), "INACTIVE");

        // marker: once this later message is processed, the duplicate (earlier in the queue) was already handled
        send(UUID.randomUUID(), second.getId(), "BLOCKED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(customers.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(CustomerStatus.BLOCKED));

        assertThat(customers.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void messageForUnknownCustomer_endsUpInDeadLetterQueue() {
        send(UUID.randomUUID(), 987654321L, "INACTIVE");

        Message dead = rabbit.receive(RabbitConfig.STATUS_DLQ, 15000);

        assertThat(dead).isNotNull();
    }

    @Test
    void malformedMessage_endsUpInDeadLetterQueue() {
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.STATUS_ROUTING_KEY, Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "CUSTOMER_STATUS_CHANGE",
                "customerId", 1,
                "status", "NOT_A_STATUS"));

        assertThat(rabbit.receive(RabbitConfig.STATUS_DLQ, 15000)).isNotNull();
    }
}
