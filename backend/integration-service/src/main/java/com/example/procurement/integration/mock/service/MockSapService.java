package com.example.procurement.integration.mock.service;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.dto.domain.SourceCursorDTO;
import com.example.procurement.integration.dto.req.CostCenterMutationReqDTO;
import com.example.procurement.integration.dto.req.ExchangeRateMutationReqDTO;
import com.example.procurement.integration.dto.resp.PageRespDTO;
import com.example.procurement.integration.dto.resp.SourceAnchorRespDTO;
import com.example.procurement.integration.dto.resp.SourcePageRespDTO;
import java.time.Instant;
public interface MockSapService {
    SourceRecordDTO save(CostCenterMutationReqDTO mutation);
    SourceRecordDTO save(ExchangeRateMutationReqDTO mutation);
    PageRespDTO<SourceRecordDTO> list(MasterDataType type, int page, int size);
    SourceAnchorRespDTO anchor(MasterDataType type);
    SourcePageRespDTO fullPage(MasterDataType type, long after, long max, int limit);
    SourcePageRespDTO changes(MasterDataType type, Instant from, Instant to, SourceCursorDTO cursor, int limit);
    SourceRecordDTO find(MasterDataType type, long id);
    void fault(MasterDataType type, boolean unavailable);
    boolean unavailable(MasterDataType type);
}
