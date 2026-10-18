package com.example.procurement.sourcing.service.ratio;

import com.example.procurement.sourcing.dto.resp.ratio.SupplierRatioResponse;

import java.util.List;

public interface SupplierRatioService {
    List<SupplierRatioResponse> list(String status, String keyword);

    SupplierRatioResponse get(Long id);
}
