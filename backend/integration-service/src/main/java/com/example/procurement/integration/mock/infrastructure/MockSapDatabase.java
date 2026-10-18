package com.example.procurement.integration.mock.infrastructure;

import com.example.procurement.integration.mock.dao.mapper.MockSapMapper;

import com.zaxxer.hikari.HikariDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 模拟系统独立资源的生命周期容器，不向主容器暴露第二个 DataSource/SqlSessionFactory。 */
public final class MockSapDatabase implements AutoCloseable {
    private final HikariDataSource source;
    private final SqlSessionTemplate session;
    private final TransactionTemplate transactions;
    public MockSapDatabase(HikariDataSource source, SqlSessionTemplate session, TransactionTemplate transactions) {
        this.source = source; this.session = session; this.transactions = transactions;
    }
    public MockSapMapper mapper() { return session.getMapper(MockSapMapper.class); }
    public TransactionTemplate transactions() { return transactions; }
    @Override
    public void close() { source.close(); }
}
