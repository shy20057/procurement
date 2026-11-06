package com.example.procurement.integration.mock.service.impl;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.*;
import com.example.procurement.integration.dto.req.CostCenterMutationReqDTO;
import com.example.procurement.integration.dto.req.ExchangeRateMutationReqDTO;
import com.example.procurement.integration.dto.resp.*;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.mock.dao.entity.MockMasterRecordDO;
import com.example.procurement.integration.mock.dao.mapper.MockSapMapper;
import com.example.procurement.integration.mock.service.MockSapService;
import com.example.procurement.integration.support.MasterDataCodec;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Source current state only; the SRM engine reads it exclusively through HTTP. */
@Service
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockSapServiceImpl implements MockSapService {
    private final MockSapMapper mapper;
    private final TransactionTemplate transactions;
    private final MasterDataCodec codec;
    private final Set<MasterDataType> unavailable = ConcurrentHashMap.newKeySet();

    public MockSapServiceImpl(MockSapMapper mapper,
            @Qualifier("mockSapTransactions") TransactionTemplate transactions, MasterDataCodec codec) {
        this.mapper = mapper;
        this.transactions = transactions;
        this.codec = codec;
    }

    @Override
    public SourceRecordDTO save(CostCenterMutationReqDTO mutation) {
        codec.require(mutation != null, "缺少请求体");
        CostCenterDTO data = codec.validate(mutation.data());
        return save(row(data), mutation.sourceId(), mutation.expectedUpdatedAt());
    }

    @Override
    public SourceRecordDTO save(ExchangeRateMutationReqDTO mutation) {
        codec.require(mutation != null, "缺少请求体");
        ExchangeRateDTO data = codec.validate(mutation.data());
        return save(row(data), mutation.sourceId(), mutation.expectedUpdatedAt());
    }

    private SourceRecordDTO save(MockMasterRecordDO row, Long sourceId, Instant expected) {
        codec.require((sourceId == null) == (expected == null), "编辑必须同时提供源ID与原始修改时间");
        codec.require(sourceId == null || sourceId > 0, "源ID必须为正数");
        try {
            return transactions.execute(status -> {
                MasterDataType type = MasterDataType.valueOf(row.getEntityType());
                if (sourceId == null) {
                    mapper.insert(row);
                } else {
                    row.setId(sourceId);
                    MockMasterRecordDO old = mapper.find(type.name(), sourceId);
                    if (old == null) throw missing();
                    codec.require(old.getBusinessKey().equals(row.getBusinessKey()), "源记录业务键不可修改，请停用旧记录后新增");
                    if (!old.getUpdatedAt().equals(expected)) throw conflict();
                    if (codec.json(record(old)).equals(codec.json(recordWithData(row, old.getUpdatedAt()))))
                        return record(old);
                    if (mapper.update(row, expected) == 0) {
                        if (mapper.find(type.name(), sourceId) == null) throw missing();
                        throw conflict();
                    }
                }
                return record(mapper.find(type.name(), row.getId()));
            });
        } catch (DuplicateKeyException e) {
            throw new IntegrationProblem("SOURCE_BUSINESS_KEY_CONFLICT", "相同业务键的源记录已存在，请读取原记录后编辑", false, 409);
        }
    }

    private IntegrationProblem conflict() {
        return new IntegrationProblem("SOURCE_TIME_CONFLICT", "SAP模拟记录已变更，请刷新后重试", false, 409);
    }
    private IntegrationProblem missing() {
        return new IntegrationProblem("SOURCE_NOT_FOUND", "SAP源记录不存在", false, 404);
    }

    @Override
    public PageRespDTO<SourceRecordDTO> list(MasterDataType type, int page, int size) {
        bounds(page, size);
        return new PageRespDTO<>(mapper.selectPage(type.name(), size, (page - 1L) * size).stream()
                .map(this::record).toList(), mapper.count(type.name()), page, size);
    }
    @Override
    public SourceAnchorRespDTO anchor(MasterDataType type) {
        available(type);
        return mapper.anchor(type.name());
    }
    @Override
    public SourcePageRespDTO fullPage(MasterDataType type, long after, long max, int limit) {
        bounds(1, limit);
        codec.require(after >= 0 && max >= after, "全量ID范围不合法");
        available(type);
        return page(mapper.fullPage(type.name(), after, max, limit + 1), limit);
    }
    @Override
    public SourcePageRespDTO changes(MasterDataType type, Instant from, Instant to, SourceCursorDTO cursor, int limit) {
        bounds(1, limit);
        codec.require(from != null && to != null && !from.isAfter(to), "增量时间窗口不合法");
        if (cursor != null) codec.require(cursor.updatedAt() != null && cursor.sourceId() > 0
                && !cursor.updatedAt().isBefore(from) && cursor.updatedAt().isBefore(to), "页游标超出时间窗口");
        available(type);
        return page(mapper.changes(type.name(), from, to, cursor == null ? null : cursor.updatedAt(),
                cursor == null ? 0 : cursor.sourceId(), limit + 1), limit);
    }
    @Override
    public SourceRecordDTO find(MasterDataType type, long id) {
        codec.require(id > 0, "源ID必须为正数");
        available(type);
        MockMasterRecordDO row = mapper.find(type.name(), id);
        if (row == null) throw missing();
        return record(row);
    }
    private SourcePageRespDTO page(List<MockMasterRecordDO> rows, int limit) {
        List<SourceRecordDTO> items = rows.stream().limit(limit).map(this::record).toList();
        SourceRecordDTO last = items.isEmpty() ? null : items.get(items.size() - 1);
        return new SourcePageRespDTO(items, last == null ? null : new SourceCursorDTO(last.updatedAt(), last.sourceId()), rows.size() > limit);
    }

    private SourceRecordDTO record(MockMasterRecordDO row) { return recordWithData(row, row.getUpdatedAt()); }
    private SourceRecordDTO recordWithData(MockMasterRecordDO row, Instant time) {
        MasterDataType type = MasterDataType.valueOf(row.getEntityType());
        CostCenterDTO cc = null;
        ExchangeRateDTO fx = null;
        if (type == MasterDataType.COST_CENTER) {
            CostCenterDTO value = codec.read(row.getPayload(), CostCenterDTO.class);
            cc = new CostCenterDTO(value.controllingArea(), value.costCenterCode(), value.companyCode(), value.name(),
                    value.validFrom(), value.validTo(), row.isBlocked(), row.isEnabled(), row.isDeleted());
        } else {
            ExchangeRateDTO value = codec.read(row.getPayload(), ExchangeRateDTO.class);
            fx = new ExchangeRateDTO(value.rateType(), value.fromCurrency(), value.toCurrency(), value.effectiveDate(),
                    value.rawRate(), value.fromFactor(), value.toFactor(), value.quotationMethod(), value.normalizedRate(),
                    row.isBlocked(), row.isEnabled(), row.isDeleted());
        }
        return new SourceRecordDTO(type, row.getBusinessKey(), row.getId(), time, cc, fx);
    }
    private MockMasterRecordDO row(CostCenterDTO data) {
        return row(MasterDataType.COST_CENTER, codec.businessKey(data), codec.json(data), data.blocked(), data.enabled(), data.isDeleted());
    }
    private MockMasterRecordDO row(ExchangeRateDTO data) {
        return row(MasterDataType.EXCHANGE_RATE, codec.businessKey(data), codec.json(data), data.blocked(), data.enabled(), data.isDeleted());
    }
    private MockMasterRecordDO row(MasterDataType type, String key, String payload, boolean blocked, boolean enabled, boolean deleted) {
        MockMasterRecordDO row = new MockMasterRecordDO();
        row.setEntityType(type.name()); row.setBusinessKey(key); row.setPayload(payload);
        row.setBlocked(blocked); row.setEnabled(enabled); row.setDeleted(deleted);
        return row;
    }
    @Override
    public void fault(MasterDataType type, boolean value) {
        if (value) unavailable.add(type); else unavailable.remove(type);
    }
    @Override
    public boolean unavailable(MasterDataType type) { return unavailable.contains(type); }
    private void available(MasterDataType type) {
        if (unavailable(type)) throw new IntegrationProblem("MOCK_SAP_UNAVAILABLE", "模拟SAP暂时不可用；请在源端面板恢复", true, 503);
    }
    private void bounds(int page, int size) { codec.require(page >= 1 && size >= 1 && size <= 1000, "分页范围不合法"); }

    @PostConstruct
    public void seed() {
        transactions.executeWithoutResult(status -> {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            // Unique business keys make concurrent demo initialization idempotent.
            if (mapper.count(MasterDataType.COST_CENTER.name()) == 0) {
                String[] codes = {"RD001", "MFG001", "FIN001"};
                String[] names = {"研发一部", "制造中心", "财务共享中心"};
                for (int i = 0; i < codes.length; i++) mapper.seed(row(codec.validate(new CostCenterDTO("A100", codes[i],
                        "1000", names[i], today.withDayOfYear(1), LocalDate.of(2099, 12, 31), false, true, false))));
            }
            if (mapper.count(MasterDataType.EXCHANGE_RATE.name()) == 0) {
                String[] currencies = {"USD", "EUR", "JPY"};
                String[] rates = {"7.1000", "7.8000", "0.0480"};
                for (int i = 0; i < currencies.length; i++) mapper.seed(row(codec.validate(new ExchangeRateDTO("M", currencies[i],
                        "CNY", today, new BigDecimal(rates[i]), BigDecimal.ONE, BigDecimal.ONE, "DIRECT", null, false, true, false))));
            }
        });
    }
}
