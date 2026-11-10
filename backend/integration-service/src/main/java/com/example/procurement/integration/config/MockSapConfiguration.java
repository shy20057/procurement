package com.example.procurement.integration.config;

import com.example.procurement.integration.mock.infrastructure.MockSapDatabase;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * 模拟 SAP 的基础设施装配，与业务规则、SQL 分开。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockSapConfiguration {


    @Bean(destroyMethod = "close")
    MockSapDatabase mockSapDatabase(
            @Value("${integration.mock-sap.jdbc-url:${spring.datasource.url}}") String url,
            @Value("${integration.mock-sap.username:${spring.datasource.username}}") String username,
            @Value("${integration.mock-sap.password:${spring.datasource.password}}") String password) throws Exception {
        // 建立连接池
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(url);
        pool.setUsername(username);
        pool.setPassword(password);
        pool.setMaximumPoolSize(3);
        pool.setPoolName("mock-sap-source");
        HikariDataSource source = new HikariDataSource(pool);

        try {
            // 跑Flyway迁移
            Flyway.configure()
                    .dataSource(source) // 用刚才创建的连接池去迁移
                    .schemas("mock_sap_schema") // 迁移脚本在这个schema里执行
                    .defaultSchema("mock_sap_schema") // 脚本里没显式写 schema 的对象,默认落在这个 schema
                    .createSchemas(true) // schema 不存在就自动建
                    .locations("classpath:db/mocksap/migration") // 迁移脚本目录 = db/mocksap/migration
                    .load() //	加载配置,此时还没连库
                    .migrate(); // 	真正执行迁移——连库、建历史表、按序跑 V1__mock_sap.sql

            return new MockSapDatabase(source, new TransactionTemplate(new DataSourceTransactionManager(source)));
        } catch (Exception failure) {
            source.close();
            throw failure;
        }
    }
}
