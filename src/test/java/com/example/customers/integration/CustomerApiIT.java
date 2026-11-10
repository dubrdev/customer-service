package com.example.customers.integration;

import com.example.customers.domain.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerApiIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository repository;

    static RequestPostProcessor admin() { return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")); }
    static RequestPostProcessor user() { return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")); }

    static String body(String name, String cpf, String email) {
        return """
               {"name":"%s","cpf":"%s","email":"%s"}""".formatted(name, cpf, email);
    }

    @BeforeEach
    void clean() { repository.deleteAll(); }

    @Test
    void admin_createsCustomer_returns201WithLocation() throws Exception {
        mvc.perform(post("/customers").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("João da Silva", "12345678901", "joao@email.com")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void duplicateCpf_returns409() throws Exception {
        String json = body("Ana", "11122233344", "ana@email.com");
        mvc.perform(post("/customers").with(admin()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mvc.perform(post("/customers").with(admin()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayload_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/customers").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "123", "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.cpf").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void userRole_cannotCreate_returns403() throws Exception {
        mvc.perform(post("/customers").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Bia", "55566677788", "bia@email.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mvc.perform(get("/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownCustomer_returns404() throws Exception {
        mvc.perform(get("/customers/999999").with(user())).andExpect(status().isNotFound());
    }

    @Test
    void searchByName_andFilterByStatus() throws Exception {
        mvc.perform(post("/customers").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(body("João Pereira", "10000000001", "jp@email.com"))).andExpect(status().isCreated());
        mvc.perform(post("/customers").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Maria Souza","cpf":"10000000002","email":"ms@email.com","status":"INACTIVE"}"""))
                .andExpect(status().isCreated());

        mvc.perform(get("/customers/search").param("name", "pereira").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("João Pereira"));

        mvc.perform(get("/customers").param("status", "INACTIVE").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Maria Souza"));

        mvc.perform(get("/customers").param("status", "INVALID").with(user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_updatesAndDeletes() throws Exception {
        String location = mvc.perform(post("/customers").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Carlos", "20000000001", "c@email.com")))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(put(location).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Carlos Alberto", "20000000001", "c@email.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carlos Alberto"));

        mvc.perform(delete(location).with(user())).andExpect(status().isForbidden());
        mvc.perform(delete(location).with(admin())).andExpect(status().isNoContent());
        mvc.perform(get(location).with(user())).andExpect(status().isNotFound());
    }
}
