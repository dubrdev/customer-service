package com.example.customers.integration;

import com.example.customers.domain.Customer;
import com.example.customers.domain.CustomerRepository;
import com.example.customers.domain.CustomerStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Every failure mode of the external score provider. */
class CustomerScoreIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository repository;
    @Autowired CacheManager cacheManager;
    @Autowired CircuitBreakerRegistry breakers;

    static RequestPostProcessor user() { return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")); }

    @BeforeEach
    void reset() {
        repository.deleteAll();
        SCORE_API.resetAll();
        Objects.requireNonNull(cacheManager.getCache("scores")).clear();
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    private Long customer(String cpf) {
        return repository.save(new Customer("Test " + cpf, cpf, cpf + "@email.com", CustomerStatus.ACTIVE)).getId();
    }

    @Test
    void returnsScore_andSecondCallIsServedFromCache() throws Exception {
        SCORE_API.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/scores/12345678901"))
                .willReturn(okJson("{\"cpf\":\"12345678901\",\"score\":750,\"classification\":\"LOW_RISK\"}")));
        Long id = customer("12345678901");

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/customers/{id}/score", id).with(user()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score").value(750))
                    .andExpect(jsonPath("$.classification").value("LOW_RISK"));
        }
        SCORE_API.verify(1, getRequestedFor(urlEqualTo("/scores/12345678901")));
    }

    @Test
    void providerReturns404_mapsTo404_withoutRetry() throws Exception {
        SCORE_API.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/scores/00000000001"))
                .willReturn(aResponse().withStatus(404)));
        Long id = customer("00000000001");

        mvc.perform(get("/customers/{id}/score", id).with(user())).andExpect(status().isNotFound());
        SCORE_API.verify(1, getRequestedFor(urlEqualTo("/scores/00000000001")));
    }

    @Test
    void providerReturns500_retriesThenReturns503() throws Exception {
        SCORE_API.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/scores/99999999999"))
                .willReturn(aResponse().withStatus(500)));
        Long id = customer("99999999999");

        mvc.perform(get("/customers/{id}/score", id).with(user()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"));
        SCORE_API.verify(3, getRequestedFor(urlEqualTo("/scores/99999999999")));   // 3 attempts
    }

    @Test
    void providerTooSlow_timesOutAndReturns503() throws Exception {
        SCORE_API.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/scores/88888888888"))
                .willReturn(okJson("{\"cpf\":\"88888888888\",\"score\":1,\"classification\":\"HIGH_RISK\"}").withFixedDelay(2000)));
        Long id = customer("88888888888");

        mvc.perform(get("/customers/{id}/score", id).with(user())).andExpect(status().isServiceUnavailable());
    }

    @Test
    void repeatedFailures_openCircuitBreaker_andFailFast() throws Exception {
        SCORE_API.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/scores/.*"))
                .willReturn(aResponse().withStatus(503)));
        Long id = customer("77777777777");

        for (int i = 0; i < 4; i++) {
            mvc.perform(get("/customers/{id}/score", id).with(user())).andExpect(status().isServiceUnavailable());
        }

        assertThat(breakers.circuitBreaker("score").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = SCORE_API.getAllServeEvents().size();
        mvc.perform(get("/customers/{id}/score", id).with(user())).andExpect(status().isServiceUnavailable());
        assertThat(SCORE_API.getAllServeEvents()).hasSize(callsBefore);            // provider no longer called
    }

    @Test
    void unknownCustomer_returns404() throws Exception {
        mvc.perform(get("/customers/{id}/score", 424242L).with(user())).andExpect(status().isNotFound());
    }
}
