package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.CostCenterDO;

import java.util.List;
import org.apache.ibatis.annotations.Param;
/** SQL 定义见 resources/mapper/integration/CostCenterMapper.xml。 */
public interface CostCenterMapper {
List<CostCenterDO> selectByKeys(@Param("keys") List<String> keys);
    int batchUpsert(@Param("records") List<CostCenterDO> records, @Param("repair") boolean repair);
    long count(@Param("search") String search);
    List<CostCenterDO> selectPage(@Param("search") String search, @Param("size") int size, @Param("offset") long offset);
}
