package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.SyncAlertDO;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/** 只声明数据库操作；业务决策和事务由 Service 负责。 */
public interface SyncAlertMapper {
int upsert(@Param("jobCode") String jobCode, @Param("runId") UUID runId,
            @Param("category") String category, @Param("summary") String summary, @Param("dedupKey") String dedupKey);
    int resolveSync(@Param("jobCode") String jobCode);
    int resolveReconciliation(@Param("jobCode") String jobCode);
    List<String> selectStaleJobCodes();
    long countOpen();
    long countAll();
    List<SyncAlertDO> selectPage(@Param("size") int size, @Param("offset") long offset);
    int acknowledge(@Param("id") long id);
}
