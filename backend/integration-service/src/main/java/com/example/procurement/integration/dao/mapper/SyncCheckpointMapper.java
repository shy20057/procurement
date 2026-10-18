package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.SyncCheckpointDO;

import org.apache.ibatis.annotations.Param;

/** 只声明数据库操作；业务决策和事务由 Service 负责。 */
public interface SyncCheckpointMapper {
SyncCheckpointDO selectByJob(@Param("jobCode") String jobCode);
    int save(@Param("jobCode") String jobCode, @Param("cursor") String cursor, @Param("complete") boolean complete);
    int markSuccess(@Param("jobCode") String jobCode);
    int resetIncomplete(@Param("jobCode") String jobCode);
}
