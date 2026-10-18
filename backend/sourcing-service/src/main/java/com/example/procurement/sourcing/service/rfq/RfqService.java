package com.example.procurement.sourcing.service.rfq;

import com.example.procurement.sourcing.dto.resp.rfq.RfqResponse;

import java.util.List;

public interface RfqService {
    List<RfqResponse> list(String status, String keyword);

    RfqResponse get(Long id);
}
