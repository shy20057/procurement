package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.SyncDifferenceDO;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
/** SQL 定义见 resources/mapper/integration/SyncReconciliationMapper.xml。 */
public interface SyncReconciliationMapper {
int deleteSeen(@Param("runId") UUID runId);
    int deleteDifferences(@Param("runId") UUID runId);
    int insertSeen(@Param("runId") UUID runId, @Param("businessKey") String businessKey);
    int insertDifference(SyncDifferenceDO difference);
    int insertMissingSource(@Param("runId") UUID runId, @Param("type") String type);
    long countDifferences(@Param("runId") UUID runId);
    long countUnrepaired(@Param("runId") UUID runId);
    List<SyncDifferenceDO> selectDifferences(@Param("runId") UUID runId);
}
