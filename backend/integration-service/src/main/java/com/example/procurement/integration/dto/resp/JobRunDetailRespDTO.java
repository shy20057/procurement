package com.example.procurement.integration.dto.resp;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 在运行基础响应上补充页审计、失败和对账明细。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobRunDetailRespDTO extends JobRunRespDTO {
    private List<SyncPageAuditRespDTO> pages;
    private List<SyncPageFailureRespDTO> failures;
    private List<SyncDifferenceRespDTO> differences;
    private long differenceCount;
}
