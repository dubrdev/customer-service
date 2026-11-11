package com.ceg.customers.score;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consulta o score de um CPF no serviço externo.
 *
 *   200                       -> devolve o score
 *   404                       -> 404 para quem chamou (não existe score para esse CPF)
 *   erro, lentidão ou offline -> 503 (o serviço de score está com problema)
 */
@Component
public class ScoreClient {

    private final RestClient rest;

    public ScoreClient(@Value("${score.base-url}") String baseUrl,
                       @Value("${score.timeout-ms}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);   // não esperar para sempre ao conectar...
        factory.setReadTimeout(timeoutMs);      // ...nem ao esperar a resposta
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public ScoreResponse fetchScore(String cpf) {
        try {
            ScoreResponse response = rest.get().uri("/scores/{cpf}", cpf).retrieve().body(ScoreResponse.class);
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum score encontrado para este cliente");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de score indisponível no momento");
    }
}
