package com.example.procurement.integration.dao.mapper;
import com.example.procurement.integration.dao.entity.SyncDifferenceDO;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;
public interface SyncReconciliationMapper {
    int insertSeen(@Param("runId") UUID runId,@Param("sourceId") long sourceId,
            @Param("businessKey") String businessKey,@Param("updatedAt") Instant updatedAt,@Param("hash") String hash);
    int insertDifference(SyncDifferenceDO difference);
    int resolveOrderDifferences(@Param("jobCode") String jobCode, @Param("sourceId") long sourceId,
            @Param("repairRunId") UUID repairRunId, @Param("applied") boolean applied);
    Long findOrderSourceId(@Param("jobCode") String jobCode, @Param("id") long id);
    Instant databaseNow();
    List<Long> missingIds(@Param("runId") UUID runId,
            @Param("maxId") long maxId,@Param("afterId") long afterId,@Param("limit") int limit);
    long countDifferences(@Param("runId") UUID runId);
    List<SyncDifferenceDO> selectDifferences(@Param("runId") UUID runId);
}
