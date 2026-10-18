package com.example.procurement.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

class GatewayRuntimeDependenciesTest {

    @Test
    void loadBalancerRuntimeIsAvailableForLbRoutes() {
        assertThat(ClassUtils.isPresent(
                        "org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer",
                        getClass().getClassLoader()))
                .as("lb:// routes require the Spring Cloud LoadBalancer runtime")
                .isTrue();
    }
}
