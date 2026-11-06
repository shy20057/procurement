package com.example.procurement.integration.mock.dao.entity;
import lombok.Data;
import java.time.Instant;
@Data
public class MockMasterRecordDO {
    private Long id;
    private String entityType;
    private String businessKey;
    private String payload;
    private boolean blocked;
    private boolean enabled;
    private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
}
