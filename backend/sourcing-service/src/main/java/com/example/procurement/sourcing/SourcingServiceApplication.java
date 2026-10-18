package com.example.procurement.sourcing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.example.procurement.sourcing.dao.mapper")
@ComponentScan(basePackages = {"com.example.procurement.sourcing"})
public class SourcingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SourcingServiceApplication.class, args);
    }
}
