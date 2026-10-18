package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.ExchangeRateDO;

import java.util.List;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Param;
/** SQL 定义见 resources/mapper/integration/ExchangeRateMapper.xml。 */
public interface ExchangeRateMapper {
List<ExchangeRateDO> selectByKeys(@Param("keys") List<String> keys);
    int batchUpsert(@Param("records") List<ExchangeRateDO> records, @Param("repair") boolean repair);
    long count(@Param("search") String search);
    List<ExchangeRateDO> selectPage(@Param("search") String search, @Param("size") int size, @Param("offset") long offset);
    ExchangeRateDO resolve(@Param("fromCurrency") String fromCurrency, @Param("toCurrency") String toCurrency,
            @Param("rateType") String rateType, @Param("businessDate") LocalDate businessDate);
}
