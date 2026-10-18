package com.example.procurement.supplier.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

class MybatisMapperScanConfigurationTest {

    @Test
    void mybatisConfigurationDoesNotDeclareASecondMapperScan() {
        assertThat(MybatisPlusConfig.class.getAnnotation(MapperScan.class))
                .as("mapper scanning is owned by SupplierServiceApplication")
                .isNull();
    }
}
