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


/**
 * 模拟 SAP 的基础设施装配，与业务规则、SQL 分开。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockSapConfiguration {


    @Bean(destroyMethod = "close") //@Bean — 把这个工厂方法的返回值注册成容器里的 bean,名字默认是方法名 mockSapDatabase  destroyMethod = "close" — 容器关闭时自动调 close()。MockSapDatabase 实现了 AutoCloseable,它的 close() 会关掉底层连接池。没有这行的话,应用停机时连接池不会优雅关闭,数据库侧会残留 TCP 连接。
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

            // 手搓MyBatis配置
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());

            // 建SqlSessionFactory
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            // 不使用 /mapper 路径，防止源端 XML 被业务 SqlSession 加载。
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:/mock-sap-mapper/*.xml"));
            SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());

            return new MockSapDatabase(
                    source, // 连接池
                    session, // SqlSessionTemplate
                    new TransactionTemplate(new DataSourceTransactionManager(source)) // 独立的事务管理器,基于 Mock 自己的数据源(所以 Mock 的事务和主业务事务互不干扰)
            );
        } catch (Exception failure) {
            source.close();
            throw failure;
        }
    }
    @Bean
    MockSapMapper mockSapMapper(MockSapDatabase database) {
        return database.mapper();
    }

    @Bean(name = "mockSapTransactions")
    TransactionTemplate mockSapTransactions(MockSapDatabase database) {
        return database.transactions();
    }
}
