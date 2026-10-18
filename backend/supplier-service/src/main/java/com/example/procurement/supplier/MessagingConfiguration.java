package com.example.procurement.supplier;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MessagingConfiguration {
    static final String EXCHANGE = "procurement.domain.v1";
    @Bean TopicExchange domainExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean Queue supplierAuditQueue() { return new Queue("supplier.audit.v1", true); }
    @Bean Binding supplierAuditBinding(Queue supplierAuditQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(supplierAuditQueue).to(domainExchange).with("supplier.#");
    }
}

