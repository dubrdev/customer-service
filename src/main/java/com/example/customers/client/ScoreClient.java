package com.example.customers.client;

import com.example.customers.api.ScoreResponse;
import com.example.customers.exception.ScoreNotFoundException;
import com.example.customers.exception.ScoreUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;

/**
 * Scenarios handled:
 *  200          -> response (cached in Redis for 10 min)
 *  404          -> ScoreNotFoundException (not retried, not counted by the circuit breaker)
 *  5xx / timeout / connection refused / invalid body -> ScoreUnavailableException (retry + circuit breaker)
 *  breaker OPEN -> CallNotPermittedException, fails fast without touching the provider
 */
@Component
public class ScoreClient {

    private final RestClient rest;

    public ScoreClient(RestClient.Builder builder, ScoreProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.readTimeout());
        this.rest = builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    @Cacheable(cacheNames = "scores", key = "#cpf")
    @Retry(name = "score")
    @CircuitBreaker(name = "score")
    public ScoreResponse fetchScore(String cpf) {
        try {
            ScoreResponse response = rest.get().uri("/scores/{cpf}", cpf).retrieve().body(ScoreResponse.class);
            if (response == null) throw new ScoreUnavailableException("Empty response from score provider");
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ScoreNotFoundException();
        } catch (RestClientException e) {
            throw new ScoreUnavailableException("Score provider call failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
