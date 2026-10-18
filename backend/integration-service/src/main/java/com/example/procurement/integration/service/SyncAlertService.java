package com.example.procurement.integration.service;
import java.util.UUID;

/** 业务能力接口：Controller、Job 入口只依赖这一层。 */
public interface SyncAlertService {
    void alert(String jobCode, UUID runId, String category, String summary);
    void staleAlerts();
    void acknowledge(long id);
    void resolveSync(String jobCode);
    void resolveReconciliation(String jobCode);
}
