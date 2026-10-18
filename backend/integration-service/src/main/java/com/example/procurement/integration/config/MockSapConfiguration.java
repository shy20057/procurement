package com.example.procurement.integration.config;

import com.example.procurement.integration.dao.typehandler.UuidTypeHandler;
import com.example.procurement.integration.mock.dao.mapper.MockSapMapper;
import com.example.procurement.integration.mock.infrastructure.MockSapDatabase;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import org.apache.ibatis.session.LocalCacheScope;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 模拟 SAP 的基础设施装配，与业务规则、SQL 分开。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockSapConfiguration {
    @Bean(destroyMethod = "close")
    MockSapDatabase mockSapDatabase(
            @Value("${integration.mock-sap.jdbc-url:${spring.datasource.url}}") String url,
            @Value("${integration.mock-sap.username:${spring.datasource.username}}") String username,
            @Value("${integration.mock-sap.password:${spring.datasource.password}}") String password) throws Exception {
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(url); pool.setUsername(username); pool.setPassword(password);
        pool.setMaximumPoolSize(3); pool.setPoolName("mock-sap-source");
        HikariDataSource source = new HikariDataSource(pool);
        try {
            Flyway.configure().dataSource(source).schemas("mock_sap_schema").defaultSchema("mock_sap_schema")
                    .createSchemas(true).locations("classpath:db/mocksap/migration").load().migrate();
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source); factory.setConfiguration(configuration);
            // 不使用 /mapper 路径，防止源端 XML 被业务 SqlSession 加载。
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:/mock-sap-mapper/*.xml"));
            SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
            return new MockSapDatabase(source, session,
                    new TransactionTemplate(new DataSourceTransactionManager(source)));
        } catch (Exception failure) {
            source.close();
            throw failure;
        }
    }

    @Bean
    MockSapMapper mockSapMapper(MockSapDatabase database) { return database.mapper(); }

    @Bean(name = "mockSapTransactions")
    TransactionTemplate mockSapTransactions(MockSapDatabase database) { return database.transactions(); }
}
