package com.example.procurement.supplier.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BillNoGenerator {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong counter = new AtomicLong(0);

    public String generateInvite() { return "SRM-INV-" + today() + "-" + seq(); }
    public String generateReg() { return "SRM-REG-" + today() + "-" + seq(); }
    public String generateCert() { return "SRM-CERT-" + today() + "-" + seq(); }
    public String generateSupplierCode() { return "SUP" + String.format("%07d", counter.incrementAndGet()); }
    public String generateChange() { return "SRM-CHG-" + today() + "-" + seq(); }
    public String generateFreeze() { return "SRM-FRZ-" + today() + "-" + seq(); }
    public String generateBlacklist() { return "SRM-BLK-" + today() + "-" + seq(); }
    public String generatePerf() { return "SRM-PERF-" + today() + "-" + seq(); }

    private String today() { return LocalDate.now().format(DATE_FMT); }
    private String seq() { return String.format("%04d", counter.incrementAndGet() % 10000); }
}
