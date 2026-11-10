package com.example.customers.service;

import com.example.customers.api.ScoreResponse;
import com.example.customers.client.ScoreClient;
import org.springframework.stereotype.Service;

/** Deliberately NOT transactional: no DB connection is held while waiting for the external HTTP call. */
@Service
public class ScoreService {

    private final CustomerService customers;
    private final ScoreClient scoreClient;

    public ScoreService(CustomerService customers, ScoreClient scoreClient) {
        this.customers = customers;
        this.scoreClient = scoreClient;
    }

    public ScoreResponse getScore(Long customerId) {
        String cpf = customers.findById(customerId).getCpf();
        return scoreClient.fetchScore(cpf);
    }
}
