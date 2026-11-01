package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.JobDefinitionDO;
import com.example.procurement.integration.dto.resp.JobDefinitionRespDTO;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/** 只声明数据库操作；业务决策和事务由 Service 负责。 */
public interface JobDefinitionMapper {
    JobDefinitionDO lockDefinition(@Param("jobCode") String jobCode);
    List<JobDefinitionRespDTO> selectDefinitions();
    int setEnabled(@Param("jobCode") String jobCode, @Param("enabled") boolean enabled);
    int insertManagementAudit(@Param("id") UUID id, @Param("jobCode") String jobCode, @Param("reason") String reason);
}
