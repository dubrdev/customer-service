package com.ceg.customers.score;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sobe um servidorzinho HTTP de mentira (do próprio Java) para fingir ser o serviço de score. */
class ScoreClientTest {

    static HttpServer servidor;
    static ScoreClient client;

    @BeforeAll
    static void sobeServidorFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/scores/12345678901", ex ->
                responde(ex, 200, "{\"cpf\":\"12345678901\",\"score\":750,\"classification\":\"LOW_RISK\"}"));
        servidor.createContext("/scores/00000000001", ex -> responde(ex, 404, "{\"error\":\"not found\"}"));
        servidor.createContext("/scores/99999999999", ex -> responde(ex, 500, "{}"));
        servidor.start();

        client = new ScoreClient("http://localhost:" + servidor.getAddress().getPort(), 1000);
    }

    @AfterAll
    static void paraServidorFalso() {
        servidor.stop(0);
    }

    private static void responde(HttpExchange ex, int status, String json) throws IOException {
        byte[] corpo = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, corpo.length);
        ex.getResponseBody().write(corpo);
        ex.close();
    }

    private void assertStatus(Throwable erro, HttpStatus esperado) {
        assertThat(erro).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    @Test
    void devolveOScoreQuandoOServicoResponde200() {
        ScoreResponse score = client.fetchScore("12345678901");

        assertThat(score.score()).isEqualTo(750);
        assertThat(score.classification()).isEqualTo("LOW_RISK");
    }

    @Test
    void devolve404QuandoNaoExisteScoreParaOCpf() {
        assertThatThrownBy(() -> client.fetchScore("00000000001")).satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void devolve503QuandoOServicoDeScoreDaErro() {
        assertThatThrownBy(() -> client.fetchScore("99999999999")).satisfies(e -> assertStatus(e, HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void devolve503QuandoOServicoDeScoreEstaForaDoAr() {
        ScoreClient semServidor = new ScoreClient("http://localhost:1", 500);   // ninguém escuta na porta 1

        assertThatThrownBy(() -> semServidor.fetchScore("12345678901")).satisfies(e -> assertStatus(e, HttpStatus.SERVICE_UNAVAILABLE));
    }
}
