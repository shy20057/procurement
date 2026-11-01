package com.example.procurement.gateway;

import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) { SpringApplication.run(ApiGatewayApplication.class, args); }
     //1
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    GlobalFilter correlationIdFilter() {
        return (exchange, chain) -> {
            String value = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
            if (value == null || value.isBlank()) value = UUID.randomUUID().toString();
            var request = exchange.getRequest().mutate().header("X-Correlation-Id", value).build();
            exchange.getResponse().getHeaders().set("X-Correlation-Id", value);
            return chain.filter(exchange.mutate().request(request).build());
        };
    }
}

