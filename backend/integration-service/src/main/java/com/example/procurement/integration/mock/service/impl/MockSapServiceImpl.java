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

/**
 * 只模拟源端业务；SRM 同步服务只能通过 HTTP 读取源端，不能注入此服务。
 */
@Service
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockSapServiceImpl implements MockSapService {
    private final MockSapMapper mapper;
    private final TransactionTemplate transactions;
    private final MasterDataCodec codec;

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
        return save(MasterDataType.COST_CENTER, codec.businessKey(data), mutation.expectedVersion(), data, null);
    }

    @Override
    public SourceRecordDTO save(ExchangeRateMutationReqDTO mutation) {
        codec.require(mutation != null, "缺少请求体");
        ExchangeRateDTO data = codec.validate(mutation.data());
        return save(MasterDataType.EXCHANGE_RATE, codec.businessKey(data), mutation.expectedVersion(), null, data);
    }

    private SourceRecordDTO save(MasterDataType type, String key, long expectedVersion, CostCenterDTO cc, ExchangeRateDTO rate) {
        return transactions.execute(status -> { //TODO 事务
            // 序号分配与提交共用流锁，避免序号顺序与提交顺序不一致导致漏同步。
            long sequence = mapper.lockStream(type.name());
            // SQL: select sequence_no from mock_sap_schema.stream_clock where entity_type=#{type} for update
            // 作用:取该类型流时钟的当前序号,并对这行加排他行锁(for update)。
            //      同一类型的并发写在这里排队,保证「分配序号」与「写变更事件」顺序一致,防止漏同步。
            Long version = mapper.selectVersion(type.name(), key);
            // SQL: select source_version from mock_sap_schema.master_record
            //        where entity_type=#{type} and business_key=#{key}
            // 作用:按业务键查这条记录当前版本号,用于乐观锁比对;查不到(返回 null)说明是新记录,按版本 0 处理。
            long current = version == null ? 0 : version;
            if (current != expectedVersion)
                throw new IntegrationProblem("SOURCE_VERSION_CONFLICT", "SAP模拟记录已变更，请刷新后重试", false, 409);
            SourceRecordDTO record = new SourceRecordDTO(type, key, current + 1, Instant.now(), cc, rate);
            String json = codec.json(record);
            mapper.upsertRecord(type.name(), key, current + 1, json);
            // SQL: insert into mock_sap_schema.master_record(entity_type,business_key,source_version,record_json)
            //        values (#{type},#{key},#{version},#{json})
            //        on conflict(entity_type,business_key) do update
            //          set source_version=excluded.source_version,record_json=excluded.record_json
            // 作用:写「当前状态」表。业务键已存在则更新版本号和整条 JSON,不存在则插入。
            //      冲突目标 (entity_type,business_key) 正是该表主键,所以同一条记录永远只有一行。
            mapper.insertChange(type.name(), sequence + 1, json);
            // SQL: insert into mock_sap_schema.change_event(entity_type,sequence_no,record_json)
            //        values (#{type},#{sequence},#{json})
            // 作用:写「变更事件流」表,序号用刚分配到的 sequence+1。
            //      同步客户端就是按这张表的 sequence_no 游标拉增量,这一行是增量数据的唯一来源。
            mapper.updateSequence(type.name(), sequence + 1);
            // SQL: update mock_sap_schema.stream_clock set sequence_no=#{sequence} where entity_type=#{type}
            // 作用:把流时钟序号推进到刚分配的序号,下一条变更从这个序号继续递增。
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
        bounds(1, limit); // 校验分页参数合法
        available(type);
        long start = type.position(after); // 把游标字符串翻译成序号（窗口的左端） 客户端SAP上次消费的位置
        long current = mapper.currentSequence(type.name()); // 读源端流时钟的当前序号（窗口的右端候选）
        long end = upper == null ? current : type.position(upper); // end = 本轮窗口上界：客户端没给 upper 就取「请求到达那一刻」的 current —— 窗口一旦定下，本轮翻页期间源端新来的数据不进本轮，归下一轮。防止长尾（客户端一直追着新数据跑）
        codec.require(start <= end && end <= current, "增量窗口游标超出范围");
        List<MockChangeDO> rows = mapper.selectChanges(type.name(), start, end, limit);// 关键，需要仔细分析一下他的sql
        long next = rows.isEmpty() ? end : rows.get(rows.size() - 1).getSequenceNo(); // 算下一页从哪开始
        return new SourcePageRespDTO(records(rows), type.cursor(next), type.cursor(end), next < end);
    }

    @Override
    public SnapshotRespDTO snapshot(MasterDataType type) {
        available(type);
        return transactions.execute(status -> {
            long position = mapper.lockStream(type.name()); // 数据同步前打快照，锁定当前同步变更的事件，在此之后变更的数据不会被同步 select sequence_no from mock_sap_schema.stream_clock where entity_type=#{type} for update
            UUID id = UUID.randomUUID(); // 这个uuid仅一个主键去重的作用 没有任何业务功能
            long count = mapper.count(type.name()); // 主数据所有的数据数量 select count(*) from mock_sap_schema.master_record where entity_type=#{type}
            mapper.insertSnapshot(id, type.name(), type.cursor(position), count); // 给快照头 拼上一个前缀 insert into mock_sap_schema.snapshot(id,entity_type,baseline_cursor,record_count) values (#{id},#{type},#{cursor},#{count})
            mapper.copySnapshot(id, type.name()); // 就是补充其对应的详情数据 insert into mock_sap_schema.snapshot_item(snapshot_id,item_no,record_json) select #{id},row_number() over(order by business_key),record_json from mock_sap_schema.master_record where entity_type=#{type}
            return new SnapshotRespDTO(id, type.cursor(position), count);
        });
    }

    @Override
    public SourcePageRespDTO snapshotPage(MasterDataType type, UUID id, long after, int limit) {
        bounds(1, limit);
        available(type);
        codec.require(after >= 0, "快照页位置不能为负数");
        MockSnapshotDO snapshot = mapper.selectSnapshot(id, type.name()); // select id,baseline_cursor,record_count from mock_sap_schema.snapsho where id=#{id} and entity_type=#{type} and created_at&gt;clock_timestamp()-interval '24 hours'
        if (snapshot == null)
            throw new IntegrationProblem("SNAPSHOT_EXPIRED", "源端快照不存在或已超过24小时，请重建未完成基线", false, 410);
        codec.require(after <= snapshot.getRecordCount(), "快照页位置超出范围");
        List<MockChangeDO> rows = mapper.selectSnapshotPage(id, after, limit);
        long next = rows.isEmpty() ? snapshot.getRecordCount() : rows.get(rows.size() - 1).getSequenceNo();
        return new SourcePageRespDTO(records(rows), Long.toString(next), snapshot.getBaselineCursor(), next < snapshot.getRecordCount());
    }

    private List<SourceRecordDTO> records(List<MockChangeDO> rows) {
        return rows.stream().map(row -> codec.read(row.getRecordJson(), SourceRecordDTO.class)).toList();
    }

    @Override
    public void fault(MasterDataType type, boolean unavailable) {
        mapper.setUnavailable(type.name(), unavailable);
    }

    @Override
    public boolean unavailable(MasterDataType type) {
        return Boolean.TRUE.equals(mapper.isUnavailable(type.name()));
    }

    private void available(MasterDataType type) {
        if (unavailable(type))
            throw new IntegrationProblem("MOCK_SAP_UNAVAILABLE", "模拟SAP暂时不可用；请在源端面板恢复", true, 503);
    }

    private void bounds(int page, int size) {
        codec.require(page >= 1 && size >= 1 && size <= 1000, "分页范围不合法");
    }

    @PostConstruct
    public void seed() {
        for (MasterDataType type : MasterDataType.values())
            transactions.executeWithoutResult(status -> {
                mapper.lockStream(type.name());
                if (mapper.count(type.name()) > 0) return;
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
                if (type == MasterDataType.COST_CENTER) {
                    String[] codes = {"RD001", "MFG001", "FIN001"};
                    String[] names = {"研发一部", "制造中心", "财务共享中心"};
                    for (int i = 0; i < codes.length; i++)
                        save(new CostCenterMutationReqDTO(new CostCenterDTO("A100", codes[i],
                                "1000", names[i], today.withDayOfYear(1), LocalDate.of(2099, 12, 31), false, false), 0));
                } else {
                    String[] currencies = {"USD", "EUR", "JPY"};
                    String[] rates = {"7.1000", "7.8000", "0.0480"};
                    for (int i = 0; i < currencies.length; i++)
                        save(new ExchangeRateMutationReqDTO(new ExchangeRateDTO("M", currencies[i],
                                "CNY", today, new BigDecimal(rates[i]), BigDecimal.ONE, BigDecimal.ONE, "DIRECT", null, false), 0));
                }
            });
    }
}
