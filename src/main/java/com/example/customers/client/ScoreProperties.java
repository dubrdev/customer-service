package com.example.customers.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "score")
public record ScoreProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) { }
