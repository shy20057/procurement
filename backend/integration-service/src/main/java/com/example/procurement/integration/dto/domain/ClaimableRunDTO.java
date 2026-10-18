package com.example.procurement.integration.dto.domain;

import java.util.UUID;
import lombok.Data;
/** run 与 definition 联查得到的内部领取投影，不对外返回。 */
@Data
public class ClaimableRunDTO {
    private UUID runId;
    private String jobCode;
    private String mode;
    private String triggerType;
    private String status;
    private Integer attemptNo;
    private String ownerId;
    private Long fenceToken;
    private String resourceKey;
    private Boolean enabled;
}
