package com.example.procurement.sourcing.service.tender;

import com.example.procurement.sourcing.dto.resp.tender.TenderResponse;

import java.util.List;

public interface TenderService {
    List<TenderResponse> list(String status, String keyword);

    TenderResponse get(Long id);
}
