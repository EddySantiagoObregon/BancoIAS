package com.bancoias.pse.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Payments payments) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Payments(int recentDefaultLimit, int recentMaxLimit) {
    }
}
