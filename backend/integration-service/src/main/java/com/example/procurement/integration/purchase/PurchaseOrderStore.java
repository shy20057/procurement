package com.example.procurement.integration.purchase;

import com.example.procurement.integration.exception.IntegrationProblem;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** ERP 字段的目标写入。业务写入和检查点提交由调用方放入同一个带围栏的页事务。 */
@Repository
public class PurchaseOrderStore {
    private static final String UPSERT_ORDER = """
            insert into procurement_schema.purchase_order as target
              (id,po_no,title,supplier_id,supplier_no,supplier_name,currency,total_amount,status,order_date,
               source_system,source_id,source_order_no,source_supplier_code,source_purchasing_org,
               source_updated_at,source_publication_status,source_payload_hash,synced_at,created_at,updated_at)
            values (:id,coalesce(:poNo,concat('EXT-',nextval('procurement_schema.external_po_no_seq'))),
                    :title,:supplierId,:supplierNo,:supplierName,:currency,:total,'SENT',:orderDate,
                    'SAP',:sourceId,:orderNo,:supplierCode,:purchasingOrg,:sourceUpdatedAt,:status,
                    :hash,clock_timestamp(),clock_timestamp(),clock_timestamp())
            on conflict (source_system,source_id) where source_system is not null
            do update set title=excluded.title,currency=excluded.currency,total_amount=excluded.total_amount,
                          order_date=excluded.order_date,source_purchasing_org=excluded.source_purchasing_org,
                          source_updated_at=excluded.source_updated_at,
                          source_publication_status=excluded.source_publication_status,
                          source_payload_hash=excluded.source_payload_hash,synced_at=excluded.synced_at,
                          updated_at=excluded.updated_at,version=target.version+1
            where target.id=excluded.id and target.supplier_id=excluded.supplier_id
              and target.source_order_no=excluded.source_order_no
              and target.source_supplier_code=excluded.source_supplier_code
              and (target.source_updated_at < excluded.source_updated_at
                   or (:allowRepair and target.source_updated_at=excluded.source_updated_at))
            """;
    private static final String UPSERT_LINE = """
            insert into procurement_schema.po_line as target
              (id,po_id,line_no,material_no,material_name,unit,quantity,unit_price,amount,required_date,
               source_line_no,line_status,created_at,updated_at)
            values (:id,:poId,:lineNo,:materialCode,:materialName,:unit,:quantity,:unitPrice,:amount,
                    :requiredDate,:sourceLineNo,:status,clock_timestamp(),clock_timestamp())
            on conflict (po_id,source_line_no) where source_line_no is not null
            do update set material_no=excluded.material_no,material_name=excluded.material_name,
                          unit=excluded.unit,quantity=excluded.quantity,unit_price=excluded.unit_price,
                          amount=excluded.amount,required_date=excluded.required_date,
                          line_status=excluded.line_status,updated_at=excluded.updated_at,version=target.version+1
            """;
    private static final Set<String> ORDER_STATUSES = Set.of("PUBLISHED", "WITHDRAWN", "CANCELLED");
    private static final Set<String> LINE_STATUSES = Set.of("ACTIVE", "CANCELLED");
    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseOrderStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int apply(List<PurchaseOrderSnapshot> orders, boolean allowSameVersionRepair) {
        if (orders.isEmpty()) return 0;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("采购订单写入必须在页事务中执行");
        }
        Set<Long> sourceIds = new HashSet<>();
        Set<String> orderNos = new HashSet<>();
        for (PurchaseOrderSnapshot order : orders) {
            validate(order);
            if (!sourceIds.add(order.sourceId()) || !orderNos.add(order.orderNo())) {
                throw invalid("同一页含重复采购订单");
            }
        }

        List<StoredOrder> existing = jdbc.query("""
                select id,po_no,source_id,source_order_no,source_supplier_code,supplier_id,
                       source_updated_at,source_payload_hash
                  from procurement_schema.purchase_order
                 where source_system='SAP' and (source_id in (:ids) or source_order_no in (:nos))
                 order by id for update
                """, Map.of("ids", sourceIds, "nos", orderNos), (rs, row) -> new StoredOrder(
                rs.getObject("id", UUID.class), rs.getString("po_no"), rs.getLong("source_id"),
                rs.getString("source_order_no"), rs.getString("source_supplier_code"),
                rs.getObject("supplier_id", UUID.class), instant(rs, "source_updated_at"),
                rs.getString("source_payload_hash")));
        Map<Long, StoredOrder> byId = new HashMap<>();
        Map<String, StoredOrder> byNumber = new HashMap<>();
        existing.forEach(order -> {
            byId.put(order.sourceId(), order);
            byNumber.put(order.sourceOrderNo(), order);
        });

        List<AcceptedOrder> accepted = new ArrayList<>();
        for (PurchaseOrderSnapshot incoming : orders) {
            StoredOrder current = byId.get(incoming.sourceId());
            StoredOrder byOrderNo = byNumber.get(incoming.orderNo());
            if (byOrderNo != null && byOrderNo.sourceId() != incoming.sourceId()
                    || current != null && (!current.sourceOrderNo().equals(incoming.orderNo())
                    || !current.sourceSupplierCode().equals(incoming.supplierCode()))) {
                throw conflict("PO_SOURCE_IDENTITY_CONFLICT", "来源订单编号或供应商与既有订单冲突");
            }
            String hash = hash(incoming);
            if (current != null) {
                int comparison = incoming.updatedAt().compareTo(current.updatedAt());
                if (comparison < 0) continue;
                if (comparison == 0 && !allowSameVersionRepair) {
                    if (!hash.equals(current.hash())) {
                        throw conflict("PO_VERSION_CONFLICT", "同一发布版本的采购订单内容不一致");
                    }
                    continue;
                }
            }
            accepted.add(new AcceptedOrder(incoming, current, hash));
        }
        if (accepted.isEmpty()) return 0;

        Map<String, Supplier> suppliers = resolveSuppliers(accepted);
        verifyLineCoverage(accepted);
        List<SqlParameterSource> headers = new ArrayList<>();
        List<SqlParameterSource> lines = new ArrayList<>();
        for (AcceptedOrder item : accepted) {
            PurchaseOrderSnapshot order = item.incoming();
            StoredOrder current = item.current();
            Supplier supplier = suppliers.get(order.supplierCode());
            if (supplier == null) {
                throw conflict("PO_SUPPLIER_NOT_FOUND", "SRM 未匹配 ERP 订单的供应商档案");
            }
            if (current != null && !current.supplierId().equals(supplier.id())) {
                throw conflict("PO_SOURCE_IDENTITY_CONFLICT", "来源供应商与既有本地档案绑定冲突");
            }
            UUID id = current == null ? UUID.randomUUID() : current.id();
            headers.add(headerParameters(item, supplier, id, allowSameVersionRepair));
            for (PurchaseOrderSnapshot.Line line : order.lines()) {
                lines.add(lineParameters(id, line));
            }
        }
        assertWritten(jdbc.batchUpdate(UPSERT_ORDER, headers.toArray(SqlParameterSource[]::new)), headers.size());
        // 整页头、行与游标仍在一个事务；分段发送避免超出 JDBC 单批参数上限。
        for (int start = 0; start < lines.size(); start += 500) {
            List<SqlParameterSource> batch = lines.subList(start, Math.min(start + 500, lines.size()));
            assertWritten(jdbc.batchUpdate(UPSERT_LINE, batch.toArray(SqlParameterSource[]::new)), batch.size());
        }
        return accepted.size();
    }

    private Map<String, Supplier> resolveSuppliers(List<AcceptedOrder> accepted) {
        Set<String> numbers = new HashSet<>();
        accepted.forEach(order -> numbers.add(order.incoming().supplierCode()));
        Map<String, Supplier> result = new HashMap<>();
        jdbc.query("""
                select id,supplier_no,legal_name from supplier_schema.supplier where supplier_no in (:numbers)
                """, Map.of("numbers", numbers), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            String number = rs.getString("supplier_no");
            result.put(number, new Supplier(rs.getObject("id", UUID.class), number, rs.getString("legal_name")));
        });
        return result;
    }

    private void verifyLineCoverage(List<AcceptedOrder> accepted) {
        Map<UUID, Set<String>> incomingByOrder = new HashMap<>();
        for (AcceptedOrder acceptedOrder : accepted) {
            if (acceptedOrder.current() == null) continue;
            Set<String> lineNos = new HashSet<>();
            acceptedOrder.incoming().lines().forEach(line -> lineNos.add(line.lineNo()));
            incomingByOrder.put(acceptedOrder.current().id(), lineNos);
        }
        if (incomingByOrder.isEmpty()) return;
        jdbc.query("""
                select po_id,source_line_no from procurement_schema.po_line
                where po_id in (:ids) and source_line_no is not null
                """, Map.of("ids", incomingByOrder.keySet()), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            if (!incomingByOrder.get(rs.getObject("po_id", UUID.class)).contains(rs.getString("source_line_no"))) {
                throw invalid("采购订单快照缺少原有明细，撤销行必须明确标记");
            }
        });
    }

    public Optional<TargetOrder> find(long sourceId) {
        return jdbc.query("""
                select h.source_id,h.source_order_no,h.title,h.source_supplier_code,h.source_purchasing_org,
                       h.order_date,h.currency,h.source_publication_status,h.source_updated_at,
                       h.total_amount,h.source_payload_hash,
                       l.source_line_no,l.material_no,l.material_name,l.quantity,l.unit,l.unit_price,
                       l.amount,l.required_date,l.line_status
                  from procurement_schema.purchase_order h
                  left join procurement_schema.po_line l on l.po_id=h.id and l.source_line_no is not null
                 where h.source_system='SAP' and h.source_id=:sourceId
                 order by l.source_line_no
                """, Map.of("sourceId", sourceId), (ResultSetExtractor<Optional<TargetOrder>>) rs -> {
            if (!rs.next()) return Optional.empty();
            long id = rs.getLong("source_id");
            String number = rs.getString("source_order_no");
            String title = rs.getString("title");
            String supplier = rs.getString("source_supplier_code");
            String org = rs.getString("source_purchasing_org");
            LocalDate date = rs.getObject("order_date", LocalDate.class);
            String currency = rs.getString("currency");
            String status = rs.getString("source_publication_status");
            Instant updatedAt = instant(rs, "source_updated_at");
            BigDecimal storedTotal = rs.getBigDecimal("total_amount");
            String storedHash = rs.getString("source_payload_hash");
            List<PurchaseOrderSnapshot.Line> lines = new ArrayList<>();
            do {
                if (rs.getString("source_line_no") != null) {
                    lines.add(new PurchaseOrderSnapshot.Line(rs.getString("source_line_no"),
                            rs.getString("material_no"), rs.getString("material_name"), rs.getBigDecimal("quantity"),
                            rs.getString("unit"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"),
                            rs.getObject("required_date", LocalDate.class), rs.getString("line_status")));
                }
            } while (rs.next());
            var snapshot = new PurchaseOrderSnapshot(id, number, title, supplier, org, date, currency,
                    status, updatedAt, List.copyOf(lines));
            BigDecimal total = lines.stream().filter(line -> "ACTIVE".equals(line.status()))
                    .map(PurchaseOrderSnapshot.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return Optional.of(new TargetOrder(snapshot, storedTotal.compareTo(total) == 0,
                    java.util.Objects.equals(storedHash, hash(snapshot))));
        });
    }

    public record TargetOrder(PurchaseOrderSnapshot snapshot, boolean totalMatches, boolean storedHashMatches) {}

    public long maxSourceId() {
        Long maximum = jdbc.queryForObject("""
                select coalesce(max(source_id),0) from procurement_schema.purchase_order where source_system='SAP'
                """, Map.of(), Long.class);
        return maximum == null ? 0 : maximum;
    }

    /** 版本时间独立比较。内容摘要与明细顺序、数值小数位及本地业务字段无关。 */
    public String hash(PurchaseOrderSnapshot order) {
        StringBuilder content = new StringBuilder();
        append(content, order.orderNo(), order.title(), order.supplierCode(), order.purchasingOrg(),
                order.orderDate(), order.currency(), order.status());
        order.lines().stream().sorted(Comparator.comparing(PurchaseOrderSnapshot.Line::lineNo)).forEach(line ->
                append(content, line.lineNo(), line.materialCode(), line.materialName(), line.quantity(), line.unit(),
                        line.unitPrice(), line.amount(), line.requiredDate(), line.status()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK 缺少 SHA-256", impossible);
        }
    }

    private static void append(StringBuilder out, Object... values) {
        for (Object value : values) {
            if (value == null) {
                out.append("-1:");
                continue;
            }
            String token = value instanceof BigDecimal number ? number.stripTrailingZeros().toPlainString()
                    : value.toString();
            out.append(token.length()).append(':').append(token);
        }
    }

    private static MapSqlParameterSource headerParameters(AcceptedOrder item, Supplier supplier, UUID id,
                                                           boolean allowRepair) {
        PurchaseOrderSnapshot order = item.incoming();
        BigDecimal total = order.lines().stream().filter(line -> "ACTIVE".equals(line.status()))
                .map(PurchaseOrderSnapshot.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!fits(total)) throw invalid("采购订单总额超出支持范围");
        return new MapSqlParameterSource("id", id)
                .addValue("poNo", item.current() == null ? null : item.current().poNo(), java.sql.Types.VARCHAR)
                .addValue("title", order.title()).addValue("supplierId", supplier.id())
                .addValue("supplierNo", supplier.number()).addValue("supplierName", supplier.name())
                .addValue("currency", order.currency()).addValue("total", total)
                .addValue("orderDate", order.orderDate()).addValue("sourceId", order.sourceId())
                .addValue("orderNo", order.orderNo()).addValue("supplierCode", order.supplierCode())
                .addValue("purchasingOrg", order.purchasingOrg())
                .addValue("sourceUpdatedAt", OffsetDateTime.ofInstant(order.updatedAt(), ZoneOffset.UTC))
                .addValue("status", order.status()).addValue("hash", item.hash()).addValue("allowRepair", allowRepair);
    }

    private static MapSqlParameterSource lineParameters(UUID orderId, PurchaseOrderSnapshot.Line line) {
        return new MapSqlParameterSource("id", UUID.randomUUID()).addValue("poId", orderId)
                .addValue("lineNo", Integer.parseInt(line.lineNo())).addValue("sourceLineNo", line.lineNo())
                .addValue("materialCode", line.materialCode()).addValue("materialName", line.materialName())
                .addValue("unit", line.unit()).addValue("quantity", line.quantity())
                .addValue("unitPrice", line.unitPrice()).addValue("amount", line.amount())
                .addValue("requiredDate", line.requiredDate()).addValue("status", line.status());
    }

    private static void validate(PurchaseOrderSnapshot order) {
        if (order == null || order.sourceId() <= 0 || !text(order.orderNo(), 40) || !text(order.title(), 200)
                || !text(order.supplierCode(), 40) || !text(order.purchasingOrg(), 32) || !text(order.currency(), 8)
                || order.orderDate() == null || order.updatedAt() == null || order.updatedAt().getNano() % 1000 != 0
                || order.status() == null || !ORDER_STATUSES.contains(order.status()) || order.lines() == null
                || order.lines().isEmpty() || order.lines().size() > 500) {
            throw invalid("ERP 采购订单头字段或整单明细数量不合法");
        }
        Set<String> lineNos = new HashSet<>();
        for (PurchaseOrderSnapshot.Line line : order.lines()) {
            if (line == null || line.lineNo() == null || !line.lineNo().matches("[0-9]{5}")
                    || !lineNos.add(line.lineNo()) || !text(line.materialCode(), 32) || !text(line.materialName(), 200)
                    || !text(line.unit(), 16) || line.requiredDate() == null || line.status() == null
                    || !LINE_STATUSES.contains(line.status()) || !fits(line.quantity()) || line.quantity().signum() <= 0
                    || !fits(line.unitPrice()) || line.unitPrice().signum() < 0
                    || !fits(line.amount()) || line.amount().signum() < 0) {
                throw invalid("ERP 采购订单明细字段不合法或行号重复");
            }
        }
    }

    private static boolean text(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private static boolean fits(BigDecimal value) {
        if (value == null) return false;
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 4 && (long) stripped.precision() - stripped.scale() <= 15;
    }

    private static void assertWritten(int[] results, int expected) {
        if (results.length != expected) throw conflict("PO_WRITE_CONFLICT", "订单批次写入结果不完整");
        for (int result : results) {
            if (result != 1 && result != Statement.SUCCESS_NO_INFO) {
                throw conflict("PO_WRITE_CONFLICT", "订单在目标端发生并发修改");
            }
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static IntegrationProblem invalid(String message) {
        return new IntegrationProblem("SOURCE_INVALID_ORDER", message, false, 422);
    }

    private static IntegrationProblem conflict(String code, String message) {
        return new IntegrationProblem(code, message, false, 409);
    }

    private record StoredOrder(UUID id, String poNo, long sourceId, String sourceOrderNo,
                               String sourceSupplierCode, UUID supplierId, Instant updatedAt, String hash) {}
    private record AcceptedOrder(PurchaseOrderSnapshot incoming, StoredOrder current, String hash) {}
    private record Supplier(UUID id, String number, String name) {}
}
