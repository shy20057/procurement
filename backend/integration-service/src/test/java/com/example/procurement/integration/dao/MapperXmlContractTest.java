package com.example.procurement.integration.dao;

import com.example.procurement.integration.dao.entity.CostCenterDO;
import com.example.procurement.integration.dao.entity.ExchangeRateDO;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.mapper.CostCenterMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.typehandler.UuidTypeHandler;
import com.example.procurement.integration.mock.dao.mapper.MockSapMapper;

import java.util.List;
import java.time.Instant;
import com.example.procurement.integration.mock.dao.entity.MockMasterRecordDO;
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
        assertThat(business.hasStatement("com.example.procurement.integration.mock.dao.mapper.MockSapMapper.anchor")).isFalse();
        Configuration mock = load("classpath*:/mock-sap-mapper/*.xml");
        for (var method : MockSapMapper.class.getDeclaredMethods())
            assertThat(mock.hasStatement(MockSapMapper.class.getName()+"."+method.getName())).isTrue();
        assertThat(mock.hasStatement(MockSapMapper.class.getName()+".lockStream")).isFalse();
        assertThat(mock.hasStatement(MockSapMapper.class.getName()+".insertSnapshot")).isFalse();
    }

    @Test
    void localClaimExcludesXxlAndKeepsSkipLocked() throws Exception {
        Configuration configuration = load("classpath*:/mapper/integration/*.xml");
        String sql = configuration.getMappedStatement("com.example.procurement.integration.dao.mapper.JobRunMapper.lockLocalCandidates")
                .getBoundSql(null).getSql().replaceAll("\\s+", " ");
        assertThat(sql).contains("r.trigger_type<>'XXL'").contains("for update of r skip locked");
    }

    @Test
    void bulkUpsertRejectsOlderTimesAndAllowsEqualTimeOnlyForExplicitRepair() throws Exception {
        Configuration configuration = load("classpath*:/mapper/integration/*.xml");
        String statement = "com.example.procurement.integration.dao.mapper.CostCenterMapper.batchUpsert";
        List<CostCenterDO> rows = List.of(new CostCenterDO(), new CostCenterDO());
        BoundSql normal = configuration.getMappedStatement(statement).getBoundSql(Map.of("records", rows, "repair", false));
        BoundSql repair = configuration.getMappedStatement(statement).getBoundSql(Map.of("records", rows, "repair", true));
        assertThat(normal.getParameterMappings()).hasSize(26);
        assertThat(normal.getSql().replaceAll("\\s+", " ")).contains("target.source_updated_at < excluded.source_updated_at");
        assertThat(repair.getSql().replaceAll("\\s+", " ")).contains("target.source_updated_at <= excluded.source_updated_at");
        String fxStatement = "com.example.procurement.integration.dao.mapper.ExchangeRateMapper.batchUpsert";
        for (boolean repairMode : List.of(false,true)) {
            String fx = configuration.getMappedStatement(fxStatement)
                    .getBoundSql(Map.of("records",List.of(new ExchangeRateDO()),"repair",repairMode))
                    .getSql().replaceAll("\\s+", " ");
            assertThat(fx).contains(repairMode ? "target.source_updated_at <= excluded.source_updated_at"
                    : "target.source_updated_at < excluded.source_updated_at");
        }
    }

    @Test
    void sourceDeltaUsesHalfOpenWindowAndStrictCompositeSeekWithoutStateFilters() throws Exception {
        Configuration configuration = load("classpath*:/mock-sap-mapper/*.xml");
        Instant start = Instant.parse("2026-01-01T00:00:00.123456Z");
        Map<String,Object> parameters = new java.util.HashMap<>();
        parameters.put("type","COST_CENTER"); parameters.put("from",start); parameters.put("to",start.plusSeconds(300));
        parameters.put("afterTime",start.plusNanos(1000)); parameters.put("afterId",9007199254740993L); parameters.put("limit",501);
        BoundSql sql = configuration.getMappedStatement(MockSapMapper.class.getName()+".changes").getBoundSql(parameters);
        String query = sql.getSql().replaceAll("\\s+", " ");
        assertThat(query).contains("mock_sap_schema.sap_master_data", "updated_at>=?", "updated_at<?", "(updated_at,id)>(?,?)", "order by updated_at,id limit ?");
        assertThat(query).doesNotContain("change_event","stream_clock","blocked=","enabled=","is_deleted=");
        parameters.put("afterTime",null);
        String firstPage = configuration.getMappedStatement(MockSapMapper.class.getName()+".changes").getBoundSql(parameters).getSql();
        assertThat(firstPage).doesNotContain("(updated_at,id)>");
    }

    @Test
    void sourceFullScanKeepsFixedIdUpperBoundAndSourceWritesKeepTimestampGuard() throws Exception {
        Configuration configuration = load("classpath*:/mock-sap-mapper/*.xml");
        String full = configuration.getMappedStatement(MockSapMapper.class.getName()+".fullPage")
                .getBoundSql(Map.of("type","COST_CENTER","after",5L,"max",99L,"limit",501)).getSql().replaceAll("\\s+"," ");
        assertThat(full).contains("id>? and id<=? order by id limit ?");
        MockMasterRecordDO row = new MockMasterRecordDO();
        String update = configuration.getMappedStatement(MockSapMapper.class.getName()+".update")
                .getBoundSql(Map.of("row",row,"expected",Instant.parse("2026-01-01T00:00:00.123456Z")))
                .getSql().replaceAll("\\s+"," ");
        assertThat(update).contains("greatest(clock_timestamp(),updated_at+interval '1 microsecond')", "id=?", "business_key=? and updated_at=?");
        assertThat(update).doesNotContain("source_version");
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
