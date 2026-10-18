package com.example.procurement.supplier.service.perf;

import com.example.procurement.supplier.dto.req.freeze.CreateFreezeRequest;
import com.example.procurement.supplier.dto.req.blacklist.CreateBlacklistRequest;
import com.example.procurement.supplier.dto.req.perf.CreatePerfRequest;
import com.example.procurement.supplier.dto.resp.blacklist.BlacklistResponse;
import com.example.procurement.supplier.dto.resp.freeze.FreezeResponse;
import com.example.procurement.supplier.dto.resp.perf.PerfResponse;
import java.util.List;

public interface PerfService {
    List<PerfResponse> list(String status, String supplierCode, String period, String grade);
    PerfResponse get(Long id);
    PerfResponse create(CreatePerfRequest request);
    void submit(Long id);
    PerfResponse confirm(Long id);
    FreezeResponse pushToFreeze(Long id, CreateFreezeRequest request);
    BlacklistResponse pushToBlacklist(Long id, CreateBlacklistRequest request);
}
