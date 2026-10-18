package com.example.procurement.integration.support;

import com.example.procurement.integration.dao.entity.CostCenterDO;
import com.example.procurement.integration.dao.entity.ExchangeRateDO;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.CostCenterDTO;
import com.example.procurement.integration.dto.domain.ExchangeRateDTO;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;

/** 外围传输模型与本地持久化模型的转换，不执行 SQL。 */
public final class MasterDataAssembler {
    private MasterDataAssembler() {}

    public static CostCenterDO costCenter(SourceRecordDTO record, String hash) {
        CostCenterDTO value = record.costCenter();
        CostCenterDO target = new CostCenterDO();
        target.setControllingArea(value.controllingArea()); target.setCostCenterCode(value.costCenterCode());
        target.setCompanyCode(value.companyCode()); target.setName(value.name());
        target.setValidFrom(value.validFrom()); target.setValidTo(value.validTo());
        target.setBlocked(value.blocked()); target.setDeleted(value.deleted());
        target.setSourceVersion(record.sourceVersion()); target.setSourceChangedAt(record.changedAt());
        target.setPayloadHash(hash);
        return target;
    }

    public static ExchangeRateDO exchangeRate(SourceRecordDTO record, String hash) {
        ExchangeRateDTO value = record.exchangeRate();
        ExchangeRateDO target = new ExchangeRateDO();
        target.setRateType(value.rateType()); target.setFromCurrency(value.fromCurrency());
        target.setToCurrency(value.toCurrency()); target.setEffectiveDate(value.effectiveDate());
        target.setRawRate(value.rawRate()); target.setFromFactor(value.fromFactor()); target.setToFactor(value.toFactor());
        target.setQuotationMethod(value.quotationMethod()); target.setNormalizedRate(value.normalizedRate());
        target.setDeleted(value.deleted()); target.setSourceVersion(record.sourceVersion());
        target.setSourceChangedAt(record.changedAt()); target.setPayloadHash(hash);
        return target;
    }

    public static SourceRecordDTO source(CostCenterDO value) {
        CostCenterDTO data = new CostCenterDTO(value.getControllingArea(), value.getCostCenterCode(),
                value.getCompanyCode(), value.getName(), value.getValidFrom(), value.getValidTo(),
                Boolean.TRUE.equals(value.getBlocked()), Boolean.TRUE.equals(value.getDeleted()));
        return new SourceRecordDTO(MasterDataType.COST_CENTER, value.getControllingArea() + "|" + value.getCostCenterCode(),
                value.getSourceVersion(), value.getSourceChangedAt(), data, null);
    }

    public static SourceRecordDTO source(ExchangeRateDO value) {
        ExchangeRateDTO data = new ExchangeRateDTO(value.getRateType(), value.getFromCurrency(), value.getToCurrency(),
                value.getEffectiveDate(), value.getRawRate(), value.getFromFactor(), value.getToFactor(),
                value.getQuotationMethod(), value.getNormalizedRate(), Boolean.TRUE.equals(value.getDeleted()));
        String key = value.getRateType() + "|" + value.getFromCurrency() + "|" + value.getToCurrency() + "|" + value.getEffectiveDate();
        return new SourceRecordDTO(MasterDataType.EXCHANGE_RATE, key, value.getSourceVersion(), value.getSourceChangedAt(), null, data);
    }
}
