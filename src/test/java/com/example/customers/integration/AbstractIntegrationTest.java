package com.example.customers.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL + RabbitMQ (Testcontainers) and a WireMock score provider, shared by all ITs. Needs Docker. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));
    static final WireMockServer SCORE_API = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        POSTGRES.start();
        RABBIT.start();
        SCORE_API.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.rabbitmq.host", RABBIT::getHost);
        r.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        r.add("spring.cache.type", () -> "simple");               // no Redis needed in tests
        r.add("score.base-url", SCORE_API::baseUrl);
        r.add("score.read-timeout", () -> "500ms");
        r.add("resilience4j.retry.instances.score.wait-duration", () -> "10ms");
        r.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "100ms");
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:1/unused");
    }
}
