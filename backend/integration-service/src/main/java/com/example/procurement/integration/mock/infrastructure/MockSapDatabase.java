package com.example.procurement.integration.mock.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** 持有独立的模拟 ERP 连接池，避免参与 SRM 页事务。 */
public final class MockSapDatabase implements AutoCloseable {
    private final HikariDataSource source;
    private final TransactionTemplate transactions;

    public MockSapDatabase(HikariDataSource source, TransactionTemplate transactions) {
        this.source = source;
        this.transactions = transactions;
    }

    public TransactionTemplate transactions() { return transactions; }

    @Override
    public void close() { source.close(); }
}
