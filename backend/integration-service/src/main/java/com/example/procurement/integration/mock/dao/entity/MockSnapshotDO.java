package com.example.procurement.integration.mock.dao.entity;
import java.util.UUID;
import lombok.Data;
@Data
public class MockSnapshotDO {
    private UUID id;
    private String baselineCursor;
    private Long recordCount;
}
