package com.example.procurement.integration.purchase.source;

import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.mock.infrastructure.MockSapDatabase;
import com.example.procurement.integration.purchase.PurchaseOrderSnapshot;
import com.example.procurement.integration.purchase.PurchaseOrderSnapshot.Line;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Anchor;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Cursor;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockPurchaseOrderService {
    static final int MAX_PAGE_SIZE = 200;
    private static final int ENVELOPE_BUDGET = 128;
    private static final String HEADER_SQL = """
            select id, order_no, title, supplier_code, purchasing_org, order_date, currency,
                   publication_status, published_updated_at
            from mock_sap_schema.purchase_order
            """;
    private static final RowMapper<Header> HEADER = (row, index) -> new Header(
            row.getLong("id"), row.getString("order_no"), row.getString("title"),
            row.getString("supplier_code"), row.getString("purchasing_org"),
            row.getObject("order_date", LocalDate.class), row.getString("currency"),
            row.getString("publication_status"), row.getObject("published_updated_at", OffsetDateTime.class).toInstant());
    private static final String SAVE_LINE_SQL = """
            insert into mock_sap_schema.purchase_order_line
              (order_id,line_no,material_code,material_name,quantity,unit,unit_price,amount,required_date,line_status)
            values (?,?,?,?,?,?,?,?,?,?)
            on conflict (order_id,line_no) do update
            set material_code=excluded.material_code, material_name=excluded.material_name,
                quantity=excluded.quantity, unit=excluded.unit, unit_price=excluded.unit_price,
                amount=excluded.amount, required_date=excluded.required_date, line_status=excluded.line_status
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TransactionTemplate reads;
    private final TransactionTemplate writes;
    private final ObjectMapper json;
    private final int pageByteLimit;

    public MockPurchaseOrderService(MockSapDatabase database, ObjectMapper json,
            @Value("${integration.purchase-order.mock-page-max-bytes:1048576}") int pageByteLimit) {
        if (pageByteLimit < 1024 || pageByteLimit > 8 * 1024 * 1024) {
            throw new IllegalArgumentException("Mock ERP采购订单页字节上限超出允许范围");
        }
        writes = database.transactions();
        if (!(writes.getTransactionManager() instanceof DataSourceTransactionManager sourceManager)) {
            throw new IllegalStateException("Mock ERP采购订单需要独立源库事务管理器");
        }
        jdbc = new JdbcTemplate(Objects.requireNonNull(sourceManager.getDataSource()));
        namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        reads = new TransactionTemplate(sourceManager);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.json = json;
        this.pageByteLimit = pageByteLimit;
    }

    public Anchor anchor() {
        return jdbc.queryForObject("""
                select clock_timestamp() as source_now,
                  coalesce((select max(id) from mock_sap_schema.purchase_order), 0) as max_source_id
                """, (row, index) -> new Anchor(row.getObject("source_now", OffsetDateTime.class).toInstant(),
                row.getLong("max_source_id")));
    }

    public Page baseline(long afterId, long maxId, int limit) {
        validateLimit(limit);
        if (afterId < 0 || maxId < afterId) throw invalid("基线订单ID范围不合法");
        return reads.execute(status -> page(jdbc.query(
                HEADER_SQL + "where id > ? and id <= ? order by id limit ?",
                HEADER, afterId, maxId, limit + 1), limit));
    }

    public Page changes(Instant startAt, Instant endAt, Cursor after, int limit) {
        validateLimit(limit);
        PurchaseOrderContract.validateWindow(startAt, endAt);
        if (after != null && (after.updatedAt() == null || after.sourceId() < 1
                || after.updatedAt().isBefore(startAt) || !after.updatedAt().isBefore(endAt))) {
            throw invalid("增量游标超出查询窗口");
        }
        return reads.execute(status -> {
            String sql = HEADER_SQL + "where published_updated_at >= ? and published_updated_at < ? ";
            String order = "order by published_updated_at,id limit ?";
            List<Header> headers = after == null
                    ? jdbc.query(sql + order, HEADER, atUtc(startAt), atUtc(endAt), limit + 1)
                    : jdbc.query(sql + "and (published_updated_at,id) > (?,?) " + order, HEADER,
                            atUtc(startAt), atUtc(endAt), atUtc(after.updatedAt()), after.sourceId(), limit + 1);
            return page(headers, limit);
        });
    }

    public Optional<PurchaseOrderSnapshot> find(long sourceId) {
        if (sourceId < 1) throw invalid("源订单ID不合法");
        return reads.execute(status -> {
            List<Header> headers = jdbc.query(HEADER_SQL + "where id = ?", HEADER, sourceId);
            return loadOrders(headers).stream().findFirst();
        });
    }

    public PurchaseOrderSnapshot publish(PublishRequest request) {
        if (request == null) throw invalid("发布订单不能为空");
        PurchaseOrderContract.validateContent(request.snapshot(0, null));
        try {
            return writes.execute(status -> publishInTransaction(request));
        } catch (DuplicateKeyException conflict) {
            throw new IntegrationProblem("SOURCE_ORDER_CONFLICT", "订单号已存在，请查询当前版本后重试", false, 409);
        }
    }

    private PurchaseOrderSnapshot publishInTransaction(PublishRequest request) {
        List<Header> existing = jdbc.query(HEADER_SQL + "where order_no = ? for update", HEADER, request.orderNo());
        Header previous = existing.isEmpty() ? null : existing.get(0);
        if (previous == null && (request.expectedUpdatedAt() != null || !"PUBLISHED".equals(request.status()))) {
            throw conflict("首次发布的订单必须为PUBLISHED，且不应携带旧版本");
        }
        if (previous != null) {
            if (!previous.updatedAt().equals(request.expectedUpdatedAt())) {
                throw conflict("订单版本已变化，请重新读取后提交");
            }
            if (!previous.supplierCode().equals(request.supplierCode())) {
                throw conflict("已发布订单的供应商不能变更");
            }
            List<String> oldLines = jdbc.queryForList(
                    "select line_no from mock_sap_schema.purchase_order_line where order_id = ?", String.class, previous.id());
            var incomingLines = request.lines().stream().map(Line::lineNo).collect(java.util.stream.Collectors.toSet());
            if (!incomingLines.containsAll(oldLines)) {
                throw invalid("发布整单必须保留既有订单行，取消行请明确标记为CANCELLED");
            }
        }
        OffsetDateTime newTime = jdbc.queryForObject("select clock_timestamp()", (row, index) -> row.getObject(1, OffsetDateTime.class));
        if (newTime == null) throw new IllegalStateException("无法读取Mock ERP时钟");
        if (previous != null && !newTime.toInstant().isAfter(previous.updatedAt())) {
            throw conflict("源时钟尚未超过上一版本，请稍后重试");
        }
        long orderId;
        if (previous == null) {
            Long id = jdbc.queryForObject("""
                    insert into mock_sap_schema.purchase_order
                      (order_no,title,supplier_code,purchasing_org,order_date,currency,publication_status,published_updated_at)
                    values (?,?,?,?,?,?,?,?) returning id
                    """, Long.class, request.orderNo(), request.title(), request.supplierCode(), request.purchasingOrg(),
                    request.orderDate(), request.currency(), request.status(), newTime);
            orderId = Objects.requireNonNull(id);
        } else {
            orderId = previous.id();
            jdbc.update("""
                    update mock_sap_schema.purchase_order
                    set title=?, purchasing_org=?, order_date=?, currency=?, publication_status=?, published_updated_at=?
                    where id=?
                    """, request.title(), request.purchasingOrg(), request.orderDate(), request.currency(),
                    request.status(), newTime, orderId);
        }
        PurchaseOrderSnapshot published = request.snapshot(orderId, newTime.toInstant());
        if (encodedSize(published) + ENVELOPE_BUDGET > pageByteLimit) throw tooLarge();
        jdbc.batchUpdate(SAVE_LINE_SQL, published.lines(), PurchaseOrderContract.MAX_LINES, (statement, line) -> {
            statement.setLong(1, orderId);
            statement.setString(2, line.lineNo());
            statement.setString(3, line.materialCode());
            statement.setString(4, line.materialName());
            statement.setBigDecimal(5, line.quantity());
            statement.setString(6, line.unit());
            statement.setBigDecimal(7, line.unitPrice());
            statement.setBigDecimal(8, line.amount());
            statement.setObject(9, line.requiredDate());
            statement.setString(10, line.status());
        });
        return published;
    }

    private Page page(List<Header> headers, int limit) {
        int count = Math.min(headers.size(), limit);
        List<PurchaseOrderSnapshot> orders = loadOrders(headers.subList(0, count));
        List<PurchaseOrderSnapshot> bounded = new ArrayList<>(count);
        int bytes = ENVELOPE_BUDGET;
        for (PurchaseOrderSnapshot order : orders) {
            int size = encodedSize(order) + 1;
            if (size > pageByteLimit - bytes) {
                if (bounded.isEmpty()) throw tooLarge();
                break;
            }
            bounded.add(order);
            bytes += size;
        }
        return new Page(List.copyOf(bounded), headers.size() > bounded.size());
    }

    private List<PurchaseOrderSnapshot> loadOrders(List<Header> headers) {
        if (headers.isEmpty()) return List.of();
        List<Long> ids = headers.stream().map(Header::id).toList();
        Map<Long, List<Line>> byOrder = new HashMap<>();
        namedJdbc.query("""
                select order_id,line_no,material_code,material_name,quantity,unit,
                       unit_price,amount,required_date,line_status
                from mock_sap_schema.purchase_order_line
                where order_id in (:ids)
                order by order_id,line_no
                """, Map.of("ids", ids), row -> {
            var lines = byOrder.computeIfAbsent(row.getLong("order_id"), ignored -> new ArrayList<>());
            if (lines.size() == PurchaseOrderContract.MAX_LINES) throw invalid("源订单行数超过允许范围");
            lines.add(new Line(row.getString("line_no"), row.getString("material_code"), row.getString("material_name"),
                    row.getBigDecimal("quantity"), row.getString("unit"), row.getBigDecimal("unit_price"),
                    row.getBigDecimal("amount"), row.getObject("required_date", LocalDate.class), row.getString("line_status")));
        });
        return headers.stream().map(header -> header.snapshot(
                List.copyOf(byOrder.getOrDefault(header.id(), List.of())))).toList();
    }

    private int encodedSize(PurchaseOrderSnapshot order) {
        try {
            return json.writeValueAsBytes(order).length;
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("无法序列化采购订单", failure);
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) throw invalid("采购订单分页大小须在1至200之间");
    }

    private OffsetDateTime atUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private IntegrationProblem invalid(String message) {
        return new IntegrationProblem("SOURCE_INVALID_ORDER", message, false, 422);
    }

    private IntegrationProblem conflict(String message) {
        return new IntegrationProblem("SOURCE_ORDER_CONFLICT", message, false, 409);
    }

    private IntegrationProblem tooLarge() {
        return new IntegrationProblem("SOURCE_ORDER_TOO_LARGE", "单张采购订单超过接口页字节上限", false, 422);
    }

    private record Header(long id, String orderNo, String title, String supplierCode, String purchasingOrg,
            LocalDate orderDate, String currency, String status, Instant updatedAt) {
        PurchaseOrderSnapshot snapshot(List<Line> lines) {
            return new PurchaseOrderSnapshot(id, orderNo, title, supplierCode, purchasingOrg, orderDate,
                    currency, status, updatedAt, lines);
        }
    }

    public record PublishRequest(String orderNo, String title, String supplierCode, String purchasingOrg,
            LocalDate orderDate, String currency, String status, Instant expectedUpdatedAt, List<Line> lines) {
        PurchaseOrderSnapshot snapshot(long id, Instant updatedAt) {
            return new PurchaseOrderSnapshot(id, orderNo, title, supplierCode, purchasingOrg, orderDate,
                    currency, status, updatedAt, lines);
        }
    }
}
