package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dto.domain.LeaseStateDTO;

import org.apache.ibatis.annotations.Param;

/** 只声明数据库操作；业务决策和事务由 Service 负责。 */
public interface JobLeaseMapper {
Long acquire(@Param("resourceKey") String resourceKey, @Param("owner") String owner, @Param("seconds") int seconds);
    Long lockOwned(@Param("resourceKey") String resourceKey, @Param("owner") String owner, @Param("token") long token);
    LeaseStateDTO lockState(@Param("resourceKey") String resourceKey);
    int renew(@Param("resourceKey") String resourceKey, @Param("owner") String owner,
            @Param("token") long token, @Param("seconds") int seconds);
    int release(@Param("resourceKey") String resourceKey, @Param("owner") String owner, @Param("token") long token);
}
