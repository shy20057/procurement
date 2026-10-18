package com.example.procurement.sourcing.service.bpa;

import com.example.procurement.sourcing.dto.resp.bpa.BpaResponse;

import java.util.List;

public interface BpaService {
    List<BpaResponse> list(String status, String keyword);

    BpaResponse get(Long id);
}
