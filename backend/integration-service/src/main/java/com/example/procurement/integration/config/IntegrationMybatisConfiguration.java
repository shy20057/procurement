package com.example.procurement.integration.config;

import com.example.procurement.integration.dao.typehandler.UuidTypeHandler;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import java.util.UUID;
import org.apache.ibatis.session.LocalCacheScope;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 两种启动模式共用的业务 Mapper 扫描；不扫描 mock 源端 Mapper。 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.example.procurement.integration.dao.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
public class  IntegrationMybatisConfiguration {
    @Bean
    ConfigurationCustomizer integrationMybatisCustomizer() {
        return configuration -> {
            configuration.setMapUnderscoreToCamelCase(true);
            // 加锁/时钟查询不能复用 SqlSession 的旧查询结果。
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
        };
    }
}
