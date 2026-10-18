package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.dao.mapper.SyncAlertMapper;
import com.example.procurement.integration.service.SyncAlertService;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SyncAlertServiceImpl implements SyncAlertService {
    private final SyncAlertMapper mapper;
    public SyncAlertServiceImpl(SyncAlertMapper mapper) { this.mapper = mapper; }

    @Override
    public void alert(String jobCode, UUID runId, String category, String summary) {
        String safe = summary == null ? "同步失败" : summary.substring(0, Math.min(500, summary.length()));
        mapper.upsert(jobCode, runId, category, safe, jobCode + ":" + category);
    }

    @Override
    public void staleAlerts() {
        for (String code : mapper.selectStaleJobCodes()) {
            alert(code, null, "STALE", "主数据超过约定时效，请执行同步并检查失败记录");
        }
    }

    @Override
    public void acknowledge(long id) { mapper.acknowledge(id); }

    @Override
    public void resolveSync(String jobCode) { mapper.resolveSync(jobCode); }

    @Override
    public void resolveReconciliation(String jobCode) { mapper.resolveReconciliation(jobCode); }
}
