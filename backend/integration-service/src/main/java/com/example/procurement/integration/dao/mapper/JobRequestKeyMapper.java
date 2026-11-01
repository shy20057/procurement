package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.JobRequestKeyDO;

import org.apache.ibatis.annotations.Param;

/** 只声明数据库操作；业务决策和事务由 Service 负责。 */
public interface JobRequestKeyMapper {
    JobRequestKeyDO selectByRequestId(@Param("requestId") String requestId);
    int insert(JobRequestKeyDO request);
}
