package com.example.procurement.integration.dao.mapper;
import com.example.procurement.integration.dao.entity.SyncDifferenceDO;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;
public interface SyncReconciliationMapper {
    int deleteSeen(@Param("runId") UUID runId);
    int deleteDifferences(@Param("runId") UUID runId);
    int insertSeen(@Param("runId") UUID runId,@Param("sourceId") long sourceId,
            @Param("businessKey") String businessKey,@Param("updatedAt") Instant updatedAt,@Param("hash") String hash);
    int insertDifference(SyncDifferenceDO difference);
    int updateDifference(SyncDifferenceDO difference);
    int resolveOrderDifferences(@Param("jobCode") String jobCode, @Param("sourceId") long sourceId,
            @Param("repairRunId") UUID repairRunId, @Param("applied") boolean applied);
    Instant firstAbsence(@Param("jobCode") String jobCode,@Param("sourceId") long sourceId);
    Instant databaseNow();
    List<Long> idsWithAbsence(@Param("jobCode") String jobCode, @Param("ids") List<Long> ids);
    List<Long> missingIds(@Param("runId") UUID runId,@Param("type") String type,
            @Param("maxId") long maxId,@Param("afterId") long afterId,@Param("limit") int limit);
    List<SyncDifferenceDO> pendingDifferences(@Param("runId") UUID runId,@Param("afterId") long afterId,@Param("limit") int limit);
    long countDifferences(@Param("runId") UUID runId);
    long countUnrepaired(@Param("runId") UUID runId);
    List<SyncDifferenceDO> selectDifferences(@Param("runId") UUID runId);
}
