package com.example.procurement.integration.dto.resp;
import com.example.procurement.integration.domain.context.SyncCheckpoint;
/** 直接继承检查点字段契约，由Controller返回具体状态。 */
public record SyncCheckpointRespDTO(@com.fasterxml.jackson.annotation.JsonUnwrapped SyncCheckpoint state) {}
