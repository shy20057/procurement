package com.example.procurement.sourcing.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * STC 单据编号生成器。
 * 规则：STC-<类型码>-<yyyyMMdd>-<4位序号>，序号取当日同前缀最大序号 + 1。
 */
@Component
public class BillNoGenerator {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 基于当日已存在的最大单号生成下一个单号。
     *
     * @param typeCode    单据类型码，如 SRC/TND/RFQ/AWD
     * @param maxExisting 当日同前缀已存在的最大单号，无则传 null
     * @return 新的单号，如 STC-SRC-20260918-0001
     */
    public String next(String typeCode, String maxExisting) {
        String base = "STC-" + typeCode + "-" + LocalDate.now().format(DATE_FMT);
        int seq = 1;
        if (maxExisting != null && maxExisting.startsWith(base + "-")) {
            try {
                seq = Integer.parseInt(maxExisting.substring(maxExisting.length() - 4)) + 1;
            } catch (NumberFormatException ignored) {
                seq = 1;
            }
        }
        return base + "-" + String.format("%04d", seq);
    }
}
