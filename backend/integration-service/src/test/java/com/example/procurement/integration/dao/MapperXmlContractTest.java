package com.example.procurement.integration.dao;

import com.example.procurement.integration.dao.entity.CostCenterDO;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.mapper.CostCenterMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.typehandler.UuidTypeHandler;
import com.example.procurement.integration.mock.dao.mapper.MockSapMapper;

import java.util.List;
import java.util.Map;
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
        assertThat(configuration.getMapperRegistry().getMappers()).hasSize(10);
        for (Class<?> mapper : configuration.getMapperRegistry().getMappers()) {
            for (var method : mapper.getDeclaredMethods()) {
                assertThat(configuration.hasStatement(mapper.getName() + "." + method.getName())).isTrue();
            }
        }
    }

    @Test
    void mockStatementsAreAvailableOnlyFromTheirSeparateResourceLocation() throws Exception {
        Configuration business = load("classpath*:/mapper/integration/*.xml");
        assertThat(business.hasStatement("com.example.procurement.integration.mock.dao.mapper.MockSapMapper.lockStream")).isFalse();
        Configuration mock = load("classpath*:/mock-sap-mapper/*.xml");
        assertThat(mock.hasStatement("com.example.procurement.integration.mock.dao.mapper.MockSapMapper.lockStream")).isTrue();
    }

    @Test
    void localClaimExcludesXxlAndKeepsSkipLocked() throws Exception {
        Configuration configuration = load("classpath*:/mapper/integration/*.xml");
        String sql = configuration.getMappedStatement("com.example.procurement.integration.dao.mapper.JobRunMapper.lockLocalCandidates")
                .getBoundSql(null).getSql().replaceAll("\\s+", " ");
        assertThat(sql).contains("r.trigger_type<>'XXL'").contains("for update of r skip locked");
    }

    @Test
    void bulkUpsertKeepsVersionGuardAndParameterizesEveryRow() throws Exception {
        Configuration configuration = load("classpath*:/mapper/integration/*.xml");
        String statement = "com.example.procurement.integration.dao.mapper.CostCenterMapper.batchUpsert";
        List<CostCenterDO> rows = List.of(new CostCenterDO(), new CostCenterDO());
        BoundSql normal = configuration.getMappedStatement(statement).getBoundSql(Map.of("records", rows, "repair", false));
        BoundSql repair = configuration.getMappedStatement(statement).getBoundSql(Map.of("records", rows, "repair", true));
        assertThat(normal.getParameterMappings()).hasSize(22);
        assertThat(normal.getSql().replaceAll("\\s+", " ")).contains("source_version < excluded.source_version");
        assertThat(repair.getSql().replaceAll("\\s+", " ")).contains("source_version <= excluded.source_version");
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
