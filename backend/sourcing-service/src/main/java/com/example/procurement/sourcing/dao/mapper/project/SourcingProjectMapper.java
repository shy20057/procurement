package com.example.procurement.sourcing.dao.mapper.project;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.procurement.sourcing.dao.entity.project.SourcingProjectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SourcingProjectMapper extends BaseMapper<SourcingProjectDO> {

    /** 查询当日同前缀已存在的最大单号，用于序号递增 */
    @Select("select max(bill_no) from stc_schema.t_stc_sourcing_project_h where bill_no like #{prefix} || '%'")
    String findMaxBillNo(@Param("prefix") String prefix);
}
