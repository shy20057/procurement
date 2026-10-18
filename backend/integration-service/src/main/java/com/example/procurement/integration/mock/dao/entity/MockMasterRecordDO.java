package com.example.procurement.integration.mock.dao.entity;
import lombok.Data;
@Data
public class MockMasterRecordDO {
    private String entityType;
    private String businessKey;
    private Long sourceVersion;
    private String recordJson;
}
