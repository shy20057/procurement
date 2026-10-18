package com.example.procurement.aggregation;

import com.example.procurement.identity.IdentityServiceApplication;
import com.example.procurement.integration.IntegrationServiceApplication;
import com.example.procurement.purchasing.ProcurementServiceApplication;
import com.example.procurement.settlement.SettlementServiceApplication;
import com.example.procurement.sourcing.SourcingServiceApplication;
import com.example.procurement.supplier.SupplierServiceApplication;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.example.procurement",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        IdentityServiceApplication.class,
                        IntegrationServiceApplication.class,
                        ProcurementServiceApplication.class,
                        SettlementServiceApplication.class,
                        SourcingServiceApplication.class,
                        SupplierServiceApplication.class
                }))
@MapperScan({"com.example.procurement.supplier.dao.mapper", "com.example.procurement.sourcing.dao.mapper"})
@EnableCaching
@EnableScheduling
public class AggregationServiceApplication {
    @Bean
    FlywayMigrationStrategy migrateBusinessSchemas(DataSource dataSource) {
        return srmFlyway -> {
            srmFlyway.migrate();
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas("stc_schema")
                    .defaultSchema("stc_schema")
                    .createSchemas(true)
                    .locations("classpath:db/stc/migration")
                    .load()
                    .migrate();
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas("integration_schema")
                    .defaultSchema("integration_schema")
                    .createSchemas(true)
                    .locations("classpath:db/integration/migration")
                    .load()
                    .migrate();
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(AggregationServiceApplication.class, args);
    }
}
