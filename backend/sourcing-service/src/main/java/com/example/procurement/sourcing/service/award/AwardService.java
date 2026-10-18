package com.example.procurement.sourcing.service.award;

import com.example.procurement.sourcing.dto.resp.award.AwardResponse;

import java.util.List;

public interface AwardService {
    List<AwardResponse> list(String status, String keyword);

    AwardResponse get(Long id);

    /** C3 风控守门审批：校验中标供应商 SRM 档案状态后置为已审核 */
    AwardResponse approve(Long id);
}
