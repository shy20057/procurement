package com.example.procurement.integration.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class XxlJobConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(XxlJobConfiguration.class);

    @Test
    void doesNotCreateExecutorWhenDisabled() {
        contextRunner.withPropertyValues("xxl.job.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(XxlJobSpringExecutor.class));
    }

    @Test
    void createsOneExecutorAndBindsPropertiesWhenEnabled() {
        new ApplicationContextRunner().withUserConfiguration(PropertyBindingConfiguration.class)
                .withPropertyValues(
                        "xxl.job.enabled=true",
                        "xxl.job.admin-addresses=http://localhost:9090/xxl-job-admin",
                        "xxl.job.access-token=test-token",
                        "xxl.job.executor.app-name=procurement-integration-executor",
                        "xxl.job.executor.address=http://host.docker.internal:9999",
                        "xxl.job.executor.port=9999",
                        "xxl.job.executor.log-path=./logs/xxl-job",
                        "xxl.job.executor.retention-days=30")
                .run(context -> {
                    XxlJobProperties properties = context.getBean(XxlJobProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.executor().appName()).isEqualTo("procurement-integration-executor");
                    assertThat(properties.executor().port()).isEqualTo(9999);
                    XxlJobSpringExecutor executor = new XxlJobConfiguration().xxlJobExecutor(properties);
                    assertThat(executor).isNotNull();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(XxlJobProperties.class)
    static class PropertyBindingConfiguration {
    }
}
