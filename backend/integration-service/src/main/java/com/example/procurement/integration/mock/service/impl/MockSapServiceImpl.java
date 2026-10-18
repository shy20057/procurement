package com.example.procurement.integration.mock.service.impl;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.CostCenterDTO;
import com.example.procurement.integration.dto.domain.ExchangeRateDTO;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.dto.req.CostCenterMutationReqDTO;
import com.example.procurement.integration.dto.req.ExchangeRateMutationReqDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.dto.resp.SnapshotRespDTO;
import com.example.procurement.integration.dto.resp.SourcePageRespDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.mock.dao.entity.MockChangeDO;
import com.example.procurement.integration.mock.dao.entity.MockSnapshotDO;
import com.example.procurement.integration.mock.dao.mapper.MockSapMapper;
import com.example.procurement.integration.mock.service.MockSapService;
import com.example.procurement.integration.support.MasterDataCodec;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 只模拟源端业务；SRM 同步服务只能通过 HTTP 读取源端，不能注入此服务。 */
@Service
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockSapServiceImpl implements MockSapService {
    private final MockSapMapper mapper;
    private final TransactionTemplate transactions;
    private final MasterDataCodec codec;

    public MockSapServiceImpl(MockSapMapper mapper,
            @Qualifier("mockSapTransactions") TransactionTemplate transactions, MasterDataCodec codec) {
        this.mapper = mapper; this.transactions = transactions; this.codec = codec;
    }

    @Override
    public SourceRecordDTO save(CostCenterMutationReqDTO mutation) {
        codec.require(mutation != null, "缺少请求体");
        CostCenterDTO data = codec.validate(mutation.data());
        return save(MasterDataType.COST_CENTER, codec.businessKey(data), mutation.expectedVersion(), data, null);
    }

    @Override
    public SourceRecordDTO save(ExchangeRateMutationReqDTO mutation) {
        codec.require(mutation != null, "缺少请求体");
        ExchangeRateDTO data = codec.validate(mutation.data());
        return save(MasterDataType.EXCHANGE_RATE, codec.businessKey(data), mutation.expectedVersion(), null, data);
    }

    private SourceRecordDTO save(MasterDataType type, String key, long expectedVersion, CostCenterDTO cc, ExchangeRateDTO rate) {
        return transactions.execute(status -> {
            // 序号分配与提交共用流锁，避免序号顺序与提交顺序不一致导致漏同步。
            long sequence = mapper.lockStream(type.name());
            Long version = mapper.selectVersion(type.name(), key);
            long current = version == null ? 0 : version;
            if (current != expectedVersion) throw new IntegrationProblem("SOURCE_VERSION_CONFLICT", "SAP模拟记录已变更，请刷新后重试", false, 409);
            SourceRecordDTO record = new SourceRecordDTO(type, key, current + 1, Instant.now(), cc, rate);
            String json = codec.json(record);
            mapper.upsertRecord(type.name(), key, current + 1, json);
            mapper.insertChange(type.name(), sequence + 1, json);
            mapper.updateSequence(type.name(), sequence + 1);
            return record;
        });
    }

    @Override
    public PageRespDTO<SourceRecordDTO> list(MasterDataType type, int page, int size) {
        bounds(page, size);
        List<SourceRecordDTO> records = mapper.selectPage(type.name(), size, (page - 1L) * size).stream()
                .map(row -> codec.read(row.getRecordJson(), SourceRecordDTO.class)).toList();
        return new PageRespDTO<>(records, mapper.count(type.name()), page, size);
    }

    @Override
    public SourcePageRespDTO changes(MasterDataType type, String after, String upper, int limit) {
        bounds(1, limit); available(type);
        long start = type.position(after);
        long current = mapper.currentSequence(type.name());
        long end = upper == null ? current : type.position(upper);
        codec.require(start <= end && end <= current, "增量窗口游标超出范围");
        List<MockChangeDO> rows = mapper.selectChanges(type.name(), start, end, limit);
        long next = rows.isEmpty() ? end : rows.get(rows.size() - 1).getSequenceNo();
        return new SourcePageRespDTO(records(rows), type.cursor(next), type.cursor(end), next < end);
    }

    @Override
    public SnapshotRespDTO snapshot(MasterDataType type) {
        available(type);
        return transactions.execute(status -> {
            long position = mapper.lockStream(type.name());
            UUID id = UUID.randomUUID();
            long count = mapper.count(type.name());
            mapper.insertSnapshot(id, type.name(), type.cursor(position), count);
            mapper.copySnapshot(id, type.name());
            return new SnapshotRespDTO(id, type.cursor(position), count);
        });
    }

    @Override
    public SourcePageRespDTO snapshotPage(MasterDataType type, UUID id, long after, int limit) {
        bounds(1, limit); available(type);
        codec.require(after >= 0, "快照页位置不能为负数");
        MockSnapshotDO snapshot = mapper.selectSnapshot(id, type.name());
        if (snapshot == null) throw new IntegrationProblem("SNAPSHOT_EXPIRED", "源端快照不存在或已超过24小时，请重建未完成基线", false, 410);
        codec.require(after <= snapshot.getRecordCount(), "快照页位置超出范围");
        List<MockChangeDO> rows = mapper.selectSnapshotPage(id, after, limit);
        long next = rows.isEmpty() ? snapshot.getRecordCount() : rows.get(rows.size() - 1).getSequenceNo();
        return new SourcePageRespDTO(records(rows), Long.toString(next), snapshot.getBaselineCursor(), next < snapshot.getRecordCount());
    }

    private List<SourceRecordDTO> records(List<MockChangeDO> rows) {
        return rows.stream().map(row -> codec.read(row.getRecordJson(), SourceRecordDTO.class)).toList();
    }

    @Override
    public void fault(MasterDataType type, boolean unavailable) { mapper.setUnavailable(type.name(), unavailable); }
    @Override
    public boolean unavailable(MasterDataType type) { return Boolean.TRUE.equals(mapper.isUnavailable(type.name())); }
    private void available(MasterDataType type) {
        if (unavailable(type)) throw new IntegrationProblem("MOCK_SAP_UNAVAILABLE", "模拟SAP暂时不可用；请在源端面板恢复", true, 503);
    }
    private void bounds(int page, int size) { codec.require(page >= 1 && size >= 1 && size <= 1000, "分页范围不合法"); }

    @PostConstruct
    public void seed() {
        for (MasterDataType type : MasterDataType.values()) transactions.executeWithoutResult(status -> {
            mapper.lockStream(type.name());
            if (mapper.count(type.name()) > 0) return;
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            if (type == MasterDataType.COST_CENTER) {
                String[] codes = {"RD001", "MFG001", "FIN001"};
                String[] names = {"研发一部", "制造中心", "财务共享中心"};
                for (int i = 0; i < codes.length; i++) save(new CostCenterMutationReqDTO(new CostCenterDTO("A100", codes[i],
                        "1000", names[i], today.withDayOfYear(1), LocalDate.of(2099, 12, 31), false, false), 0));
            } else {
                String[] currencies = {"USD", "EUR", "JPY"};
                String[] rates = {"7.1000", "7.8000", "0.0480"};
                for (int i = 0; i < currencies.length; i++) save(new ExchangeRateMutationReqDTO(new ExchangeRateDTO("M", currencies[i],
                        "CNY", today, new BigDecimal(rates[i]), BigDecimal.ONE, BigDecimal.ONE, "DIRECT", null, false), 0));
            }
        });
    }
}
