package com.ceg.customers.customer;

import com.ceg.customers.score.ScoreClient;
import com.ceg.customers.score.ScoreResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa só a camada HTTP (rotas, status e JSON). O serviço e o cliente de score são "de mentira". */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CustomerService service;
    @MockitoBean ScoreClient scoreClient;

    private Customer cliente(Long id) {
        Customer c = new Customer("João da Silva", "12345678901", "joao@email.com", CustomerStatus.ACTIVE);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    void postValidoDevolve201() throws Exception {
        when(service.create(any(CustomerRequest.class))).thenReturn(cliente(1L));

        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"João da Silva","cpf":"12345678901","email":"joao@email.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void postComCpfInvalidoDevolve400() throws Exception {
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"João","cpf":"123","email":"joao@email.com"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void postComEmailInvalidoDevolve400() throws Exception {
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"João","cpf":"12345678901","email":"isso-nao-e-email"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDeClienteExistenteDevolve200() throws Exception {
        when(service.findById(1L)).thenReturn(cliente(1L));

        mvc.perform(get("/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("João da Silva"));
    }

    @Test
    void getDeClienteInexistenteDevolve404() throws Exception {
        when(service.findById(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente 99 não encontrado"));

        mvc.perform(get("/customers/99")).andExpect(status().isNotFound());
    }

    @Test
    void listaDevolveUmArrayDeClientes() throws Exception {
        when(service.list(null, 0, 20)).thenReturn(List.of(cliente(1L), cliente(2L)));

        mvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void deleteDevolve204() throws Exception {
        mvc.perform(delete("/customers/1")).andExpect(status().isNoContent());
    }

    @Test
    void scoreDevolve200() throws Exception {
        when(service.findById(1L)).thenReturn(cliente(1L));
        when(scoreClient.fetchScore("12345678901")).thenReturn(new ScoreResponse("12345678901", 750, "LOW_RISK"));

        mvc.perform(get("/customers/1/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(750))
                .andExpect(jsonPath("$.classification").value("LOW_RISK"));
    }

    @Test
    void scoreComServicoForaDoArDevolve503() throws Exception {
        when(service.findById(1L)).thenReturn(cliente(1L));
        when(scoreClient.fetchScore("12345678901"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de score indisponível"));

        mvc.perform(get("/customers/1/score")).andExpect(status().isServiceUnavailable());
    }
}
