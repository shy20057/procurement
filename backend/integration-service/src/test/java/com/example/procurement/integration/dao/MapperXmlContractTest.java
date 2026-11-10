package com.example.procurement.integration.dao;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.typehandler.UuidTypeHandler;

import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/** 解析真实 XML 并生成 BoundSql，不建立数据库连接。 */
class MapperXmlContractTest {
    private Configuration load(String pattern) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(pattern)) {
            try (var stream = resource.getInputStream()) {
                new XMLMapperBuilder(stream, configuration, resource.toString(), configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    @Test
    void everyBusinessMapperMethodHasALoadableStatement() throws Exception {
        Configuration configuration = load("classpath*:/mapper/integration/*.xml");
        assertThat(configuration.getMapperRegistry().getMappers()).hasSize(8);
        for (Class<?> mapper : configuration.getMapperRegistry().getMappers()) {
            for (var method : mapper.getDeclaredMethods()) {
                assertThat(configuration.hasStatement(mapper.getName() + "." + method.getName())).isTrue();
            }
        }
    }

    @Test
    void uuidParametersUsePostgresObjectBinding() throws Exception {
        Configuration configuration = load("classpath*:/mapper/integration/*.xml");
        JobRunDO run = new JobRunDO(); run.setRunId(UUID.randomUUID());
        BoundSql sql = configuration.getMappedStatement("com.example.procurement.integration.dao.mapper.JobRunMapper.insert").getBoundSql(run);
        assertThat(sql.getParameterMappings().stream().filter(parameter -> parameter.getProperty().equals("runId"))
                .findFirst().orElseThrow().getTypeHandler()).isInstanceOf(UuidTypeHandler.class);
    }
}
