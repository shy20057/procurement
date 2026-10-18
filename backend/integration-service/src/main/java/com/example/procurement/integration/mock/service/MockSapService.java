package com.example.procurement.integration.mock.service;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.dto.req.CostCenterMutationReqDTO;
import com.example.procurement.integration.dto.req.ExchangeRateMutationReqDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.dto.resp.SnapshotRespDTO;
import com.example.procurement.integration.dto.resp.SourcePageRespDTO;

import java.util.UUID;

public interface MockSapService {
    SourceRecordDTO save(CostCenterMutationReqDTO mutation);
    SourceRecordDTO save(ExchangeRateMutationReqDTO mutation);
    PageRespDTO<SourceRecordDTO> list(MasterDataType type, int page, int size);
    SourcePageRespDTO changes(MasterDataType type, String after, String upper, int limit);
    SnapshotRespDTO snapshot(MasterDataType type);
    SourcePageRespDTO snapshotPage(MasterDataType type, UUID id, long after, int limit);
    void fault(MasterDataType type, boolean unavailable);
    boolean unavailable(MasterDataType type);
}
