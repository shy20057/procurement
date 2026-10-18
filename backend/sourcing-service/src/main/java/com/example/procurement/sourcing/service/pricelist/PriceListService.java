package com.example.procurement.sourcing.service.pricelist;

import com.example.procurement.sourcing.dto.resp.pricelist.PriceListResponse;

import java.util.List;

public interface PriceListService {
    List<PriceListResponse> list(String status, String keyword);

    PriceListResponse get(Long id);
}
