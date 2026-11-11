package com.ceg.customers.customer;

import com.ceg.customers.messaging.CustomerEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository repository;
    @Mock CustomerEventPublisher publisher;
    @InjectMocks CustomerService service;

    private final CustomerRequest request =
            new CustomerRequest("João da Silva", "12345678901", "joao@email.com", null);

    /** Simula o banco: devolve o mesmo cliente, já com um id. */
    private Customer comId(Customer c, Long id) {
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private void assertStatus(Throwable erro, HttpStatus esperado) {
        assertThat(erro).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    @Test
    void criaClienteComStatusAtivoPorPadraoEAvisaORabbit() {
        when(repository.existsByCpf("12345678901")).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(inv -> comId(inv.getArgument(0), 1L));

        Customer criado = service.create(request);

        assertThat(criado.getId()).isEqualTo(1L);
        assertThat(criado.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        verify(publisher).customerCreated(1L);
    }

    @Test
    void naoCriaClienteComCpfJaCadastrado() {
        when(repository.existsByCpf("12345678901")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request)).satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));

        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void buscaClienteInexistenteDa404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L)).satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void atualizaOsDadosDeUmClienteExistente() {
        Customer existente = comId(new Customer("Nome Antigo", "11111111111", "antigo@email.com", CustomerStatus.BLOCKED), 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(existente));
        when(repository.existsByCpfAndIdNot("12345678901", 5L)).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer atualizado = service.update(5L, request);

        assertThat(atualizado.getName()).isEqualTo("João da Silva");
        assertThat(atualizado.getCpf()).isEqualTo("12345678901");
        assertThat(atualizado.getStatus()).isEqualTo(CustomerStatus.BLOCKED);   // status não veio no pedido: mantém
    }

    @Test
    void naoAtualizaParaUmCpfQueOutroClienteJaUsa() {
        Customer existente = comId(new Customer("Maria", "22222222222", "maria@email.com", CustomerStatus.ACTIVE), 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(existente));
        when(repository.existsByCpfAndIdNot("12345678901", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, request)).satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));

        verify(repository, never()).save(any());
    }

    @Test
    void removeClienteExistente() {
        when(repository.existsById(7L)).thenReturn(true);

        service.delete(7L);

        verify(repository).deleteById(7L);
    }

    @Test
    void removerClienteInexistenteDa404() {
        when(repository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(7L)).satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));

        verify(repository, never()).deleteById(any());
    }

    @Test
    void listaSemFiltroUsaFindAll() {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.list(null, 0, 20)).isEmpty();

        verify(repository).findAll(any(Pageable.class));
    }

    @Test
    void listaComFiltroDeStatusUsaFindByStatus() {
        when(repository.findByStatus(eq(CustomerStatus.BLOCKED), any(Pageable.class))).thenReturn(List.of());

        assertThat(service.list(CustomerStatus.BLOCKED, 0, 20)).isEmpty();

        verify(repository).findByStatus(eq(CustomerStatus.BLOCKED), any(Pageable.class));
    }

    @Test
    void mudaOStatusDeUmClienteExistente() {
        Customer cliente = comId(new Customer("Ana", "33333333333", "ana@email.com", CustomerStatus.ACTIVE), 3L);
        when(repository.findById(3L)).thenReturn(Optional.of(cliente));

        boolean mudou = service.changeStatus(3L, CustomerStatus.INACTIVE);

        assertThat(mudou).isTrue();
        assertThat(cliente.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        verify(repository).save(cliente);
    }

    @Test
    void mudarStatusDeClienteInexistenteNaoFazNada() {
        when(repository.findById(3L)).thenReturn(Optional.empty());

        assertThat(service.changeStatus(3L, CustomerStatus.INACTIVE)).isFalse();

        verify(repository, never()).save(any());
    }
}
