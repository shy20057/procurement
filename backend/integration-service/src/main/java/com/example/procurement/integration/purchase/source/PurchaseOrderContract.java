package com.example.procurement.integration.purchase.source;

import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public final class PurchaseOrderContract {
    public static final int MAX_LINES = 500;
    private static final Set<String> PUBLICATION_STATES = Set.of("PUBLISHED", "WITHDRAWN", "CANCELLED");
    private static final Set<String> LINE_STATES = Set.of("ACTIVE", "CANCELLED");

    private PurchaseOrderContract() {}

    public static void validate(PurchaseOrderSnapshot order) {
        require(order != null, "订单为空");
        require(order.sourceId() > 0 && order.updatedAt() != null, "订单缺少源端ID或版本");
        require(order.updatedAt().getNano() % 1000 == 0, "源端订单版本最多支持微秒精度");
        validateContent(order);
    }

    static void validateContent(PurchaseOrderSnapshot order) {
        requireText(order.orderNo(), 40, "订单号");
        requireText(order.title(), 200, "订单标题");
        requireText(order.supplierCode(), 40, "供应商编码");
        requireText(order.purchasingOrg(), 32, "采购组织");
        require(order.orderDate() != null, "订单日期不能为空");
        requireText(order.currency(), 8, "币种");
        require(order.status() != null && PUBLICATION_STATES.contains(order.status()), "订单发布状态不合法");
        require(order.lines() != null && !order.lines().isEmpty() && order.lines().size() <= MAX_LINES, "订单行数超出允许范围");

        Set<String> lineNumbers = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderSnapshot.Line line : order.lines()) {
            require(line != null && line.lineNo() != null && line.lineNo().matches("[0-9]{5}"), "订单行号须为5位数字");
            require(lineNumbers.add(line.lineNo()), "订单行号重复");
            requireText(line.materialCode(), 32, "物料编码");
            requireText(line.materialName(), 200, "物料名称");
            requireText(line.unit(), 16, "计量单位");
            require(line.requiredDate() != null, "需求日期不能为空");
            require(line.status() != null && LINE_STATES.contains(line.status()), "订单行状态不合法");
            require(validDecimal(line.quantity()) && line.quantity().signum() > 0, "订单数量不合法");
            require(validDecimal(line.unitPrice()) && line.unitPrice().signum() >= 0, "单价不合法");
            require(validDecimal(line.amount()) && line.amount().signum() >= 0, "行金额不合法");
            if ("ACTIVE".equals(line.status())) total = total.add(line.amount());
        }
        require(validDecimal(total), "有效订单行总金额超出范围");
    }

    static void validateWindow(Instant start, Instant end) {
        require(start != null && end != null && start.isBefore(end), "增量查询时间窗口不合法");
    }

    private static boolean validDecimal(BigDecimal value) {
        if (value == null) return false;
        BigDecimal canonical = value.stripTrailingZeros();
        return canonical.scale() <= 4 && (long) canonical.precision() - canonical.scale() <= 15;
    }

    private static void requireText(String value, int limit, String field) {
        require(value != null && !value.isBlank() && value.length() <= limit, field + "不合法");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IntegrationProblem("SOURCE_INVALID_ORDER", message, false, 422);
    }
}
