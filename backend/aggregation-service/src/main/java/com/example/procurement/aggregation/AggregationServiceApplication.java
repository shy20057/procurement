package com.example.procurement.aggregation;

import com.example.procurement.identity.IdentityServiceApplication;
import com.example.procurement.integration.IntegrationServiceApplication;
import com.example.procurement.purchasing.ProcurementServiceApplication;
import com.example.procurement.settlement.SettlementServiceApplication;
import com.example.procurement.sourcing.SourcingServiceApplication;
import com.example.procurement.supplier.SupplierServiceApplication;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.example.procurement",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        IdentityServiceApplication.class,
                        IntegrationServiceApplication.class,
                        ProcurementServiceApplication.class,
                        SettlementServiceApplication.class,
                        SourcingServiceApplication.class,
                        SupplierServiceApplication.class
                }))
@MapperScan({"com.example.procurement.supplier.dao.mapper", "com.example.procurement.sourcing.dao.mapper"})
@EnableCaching
public class AggregationServiceApplication {
    private static final String BUSINESS_HISTORY = "aggregate_business_history.flyway_schema_history";
    private static final long BUSINESS_MIGRATION_LOCK = 71506400821L;
    private static final List<String> LEGACY_TABLES = List.of(
            "supplier_schema.supplier", "supplier_schema.outbox_event", "workflow_schema.demo_order_flow",
            "master_schema.material", "master_schema.organization", "master_schema.employee",
            "supplier_schema.qualification", "supplier_schema.supplier_bank_account", "supplier_schema.sensitive_change",
            "supplier_schema.performance_record", "sourcing_schema.sourcing_project", "sourcing_schema.rfq",
            "sourcing_schema.rfq_invitation", "sourcing_schema.rfq_line", "sourcing_schema.quotation",
            "sourcing_schema.quotation_line", "sourcing_schema.award", "sourcing_schema.bpa",
            "master_schema.price_list", "master_schema.supplier_quota", "procurement_schema.purchase_requisition",
            "procurement_schema.pr_line", "procurement_schema.purchase_order", "procurement_schema.po_line",
            "procurement_schema.goods_receipt", "procurement_schema.receipt_line", "procurement_schema.service_entry",
            "procurement_schema.service_entry_line", "settlement_schema.invoice", "settlement_schema.invoice_line",
            "settlement_schema.payment_request");

    @Bean
    FlywayMigrationStrategy migrateBusinessSchemas(DataSource dataSource) {
        return srmFlyway -> {
            srmFlyway.migrate();
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas("stc_schema")
                    .defaultSchema("stc_schema")
                    .createSchemas(true)
                    .locations("classpath:db/stc/migration")
                    .load()
                    .migrate();
            migrateAggregateBusiness(dataSource);
            Flyway integrationFlyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas("integration_schema")
                    .defaultSchema("integration_schema")
                    .createSchemas(true)
                    .locations("classpath:db/integration/migration")
                    .load();
            guardDestructiveLegacyIntegrationUpgrade(dataSource, integrationFlyway);
            integrationFlyway.migrate();
        };
    }

    private static void guardDestructiveLegacyIntegrationUpgrade(DataSource dataSource, Flyway flyway) {
        boolean pending = Arrays.stream(flyway.info().pending())
                .anyMatch(migration -> MigrationVersion.fromVersion("4").equals(migration.getVersion()));
        if (!pending) return;

        // 历史 V4 会清空同步演示表。启动新订单模块不能隐式批准清理已有记录。
        List<String> affected = List.of("job_request_key", "sync_page_audit", "sync_page_failure",
                "sync_alert", "sync_reconciliation_seen", "sync_reconciliation_diff", "management_audit",
                "job_run", "cost_center", "exchange_rate");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String table : affected) {
                String qualified = "integration_schema." + table;
                if (!tableExists(connection, qualified)) continue;
                try (ResultSet rows = statement.executeQuery("select exists(select 1 from " + qualified + ")")) {
                    if (rows.next() && rows.getBoolean(1)) {
                        throw new IllegalStateException("历史 Integration V4 会清理 " + qualified
                                + " 的记录，请先确认旧数据迁移方案");
                    }
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("历史 Integration V4 数据安全检查失败", error);
        }
    }

    private static void migrateAggregateBusiness(DataSource dataSource) {
        Flyway businessFlyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("aggregate_business_history")
                .defaultSchema("aggregate_business_history")
                .createSchemas(true)
                .baselineOnMigrate(false)
                .baselineVersion(MigrationVersion.fromVersion("3"))
                .locations("classpath:db/migration")
                .load();

        // 两个聚合实例启动时，基线探测和首次迁移也必须串行。
        try (Connection connection = dataSource.getConnection()) {
            advisoryLock(connection, "select pg_advisory_lock(?)");
            try {
                if (!tableExists(connection, BUSINESS_HISTORY)) {
                    int existing = 0;
                    for (String table : LEGACY_TABLES) {
                        if (tableExists(connection, table)) existing++;
                    }
                    if (existing != 0) {
                        if (existing != LEGACY_TABLES.size()) {
                            throw new IllegalStateException("采购基础业务表仅部分存在，需确认 V1–V3 的历史状态后再迁移");
                        }
                        verifyLegacyOrderColumns(connection);
                        if (hasColumn(connection, "procurement_schema", "purchase_order", "source_system")
                                || hasColumn(connection, "procurement_schema", "po_line", "source_line_no")) {
                            throw new IllegalStateException("采购订单已含外部来源字段但缺少聚合迁移历史，需确认迁移来源");
                        }
                        businessFlyway.baseline();
                    }
                }
                businessFlyway.migrate();
            } finally {
                advisoryLock(connection, "select pg_advisory_unlock(?)");
            }
        } catch (SQLException error) {
            throw new IllegalStateException("采购业务库迁移前检查失败", error);
        }
    }

    private static void verifyLegacyOrderColumns(Connection connection) throws SQLException {
        requireColumns(connection, "supplier_schema", "supplier",
                List.of("id", "supplier_no", "legal_name"));
        requireColumns(connection, "procurement_schema", "purchase_order",
                List.of("id", "po_no", "title", "supplier_id", "supplier_no", "supplier_name", "currency",
                        "total_amount", "status", "order_date", "version", "created_at", "updated_at"));
        requireColumns(connection, "procurement_schema", "po_line",
                List.of("id", "po_id", "line_no", "material_no", "material_name", "unit", "quantity",
                        "unit_price", "amount", "required_date", "received_quantity", "version",
                        "created_at", "updated_at"));
    }

    private static void requireColumns(Connection connection, String schema, String table, List<String> names)
            throws SQLException {
        Set<String> missing = new HashSet<>(names);
        try (PreparedStatement sql = connection.prepareStatement("""
                select column_name from information_schema.columns where table_schema=? and table_name=?
                """)) {
            sql.setString(1, schema);
            sql.setString(2, table);
            try (ResultSet rows = sql.executeQuery()) {
                while (rows.next()) missing.remove(rows.getString(1));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("旧采购业务表结构与基线不符：" + schema + "." + table + " 缺少 " + missing);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement sql = connection.prepareStatement("""
                select exists(select 1 from pg_class where oid=to_regclass(?) and relkind in ('r','p'))
                """)) {
            sql.setString(1, table);
            try (ResultSet row = sql.executeQuery()) {
                return row.next() && row.getBoolean(1);
            }
        }
    }

    private static boolean hasColumn(Connection connection, String schema, String table, String column)
            throws SQLException {
        try (PreparedStatement sql = connection.prepareStatement("""
                select exists(select 1 from information_schema.columns
                    where table_schema=? and table_name=? and column_name=?)
                """)) {
            sql.setString(1, schema);
            sql.setString(2, table);
            sql.setString(3, column);
            try (ResultSet row = sql.executeQuery()) {
                return row.next() && row.getBoolean(1);
            }
        }
    }

    private static void advisoryLock(Connection connection, String command) throws SQLException {
        try (PreparedStatement sql = connection.prepareStatement(command)) {
            sql.setLong(1, BUSINESS_MIGRATION_LOCK);
            sql.execute();
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(AggregationServiceApplication.class, args);
    }
}
