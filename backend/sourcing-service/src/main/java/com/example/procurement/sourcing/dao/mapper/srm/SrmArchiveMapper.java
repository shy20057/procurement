package com.example.procurement.sourcing.dao.mapper.srm;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SRM 供应商档案只读查询（同库跨 schema）。
 * 用于 C3 风控守门与档案状态/绩效评级的实时引用。
 */
@Mapper
public interface SrmArchiveMapper {

    /** 按供应商编码查询档案认证状态（POTENTIAL/TEMP_QUALIFIED/QUALIFIED/FROZEN/BLACKLIST），无档案返回 null */
    @Select("select cert_status from srm_schema.t_srm_supplier_archive_h where supplier_code = #{supplierCode}")
    String findCertStatus(@Param("supplierCode") String supplierCode);

    /** 按供应商编码查询档案绩效评级（A/B/C），无档案或无评级返回 null */
    @Select("select perf_grade from srm_schema.t_srm_supplier_archive_h where supplier_code = #{supplierCode}")
    String findPerfGrade(@Param("supplierCode") String supplierCode);
}
