package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.SyncPageAuditDO;
import com.example.procurement.integration.dao.entity.SyncPageFailureDO;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/** 只声明数据库操作；业务决策和事务由 Service 负责。 */
public interface SyncAuditMapper {
int nextPageNo(@Param("runId") UUID runId);
    int insertPage(SyncPageAuditDO page);
    int insertFailure(SyncPageFailureDO failure);
    List<SyncPageAuditDO> selectPages(@Param("runId") UUID runId);
    List<SyncPageFailureDO> selectFailures(@Param("runId") UUID runId);
    int deletePages(@Param("runId") UUID runId);
}
