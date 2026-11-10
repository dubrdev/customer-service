package com.example.customers.api;

import java.io.Serializable;

public record ScoreResponse(String cpf, Integer score, String classification) implements Serializable { }
