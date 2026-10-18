package com.example.procurement.integration.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.mapper.CostCenterMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.mock.dao.mapper.MockSapMapper;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 加载真实MyBatis自动配置，不启动Web应用、不获取数据库连接。 */
class IntegrationMybatisConfigurationTest {
    @Test
    void autoConfigurationLoadsBusinessMappersAndBindsUuidWithoutLoadingMockMappers() {
        DataSource source = mock(DataSource.class);
        new ApplicationContextRunner()
                .withBean(DataSource.class, () -> source)
                .withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(IntegrationMybatisConfiguration.class)
                .withPropertyValues("mybatis-plus.mapper-locations=classpath*:/mapper/integration/*.xml")
                .run(context -> {
                    assertThat(context).hasSingleBean(SqlSessionFactory.class)
                            .hasSingleBean(JobRunMapper.class).hasSingleBean(CostCenterMapper.class)
                            .doesNotHaveBean(MockSapMapper.class);
                    var configuration = context.getBean(SqlSessionFactory.class).getConfiguration();
                    JobRunDO run = new JobRunDO(); run.setRunId(UUID.randomUUID());
                    var statement = configuration.getMappedStatement(JobRunMapper.class.getName() + ".insert");
                    PreparedStatement prepared = mock(PreparedStatement.class);
                    new DefaultParameterHandler(statement, run, statement.getBoundSql(run)).setParameters(prepared);
                    verify(prepared).setObject(1, run.getRunId(), Types.OTHER);
                    verify(source, never()).getConnection();
                });
    }
}
