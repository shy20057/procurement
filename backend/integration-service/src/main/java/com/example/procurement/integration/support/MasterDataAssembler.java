package com.example.procurement.integration.support;

import com.example.procurement.integration.dao.entity.CostCenterDO;
import com.example.procurement.integration.dao.entity.ExchangeRateDO;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.*;
import java.util.List;

/** 传输模型与本地模型映射，不执行SQL。 */
public final class MasterDataAssembler {
    private MasterDataAssembler() {}
    public static CostCenterDO costCenter(SourceRecordDTO record,String hash) {
        CostCenterDTO v=record.costCenter(); CostCenterDO t=new CostCenterDO();
        t.setSourceSystem("SAP");t.setSourceId(record.sourceId());
        t.setControllingArea(v.controllingArea());t.setCostCenterCode(v.costCenterCode());
        t.setCompanyCode(v.companyCode());t.setName(v.name());t.setValidFrom(v.validFrom());t.setValidTo(v.validTo());
        t.setBlocked(v.blocked());t.setEnabled(v.enabled());t.setIsDeleted(v.isDeleted());
        t.setSourceUpdatedAt(record.updatedAt());t.setPayloadHash(hash);return t;
    }
    public static ExchangeRateDO exchangeRate(SourceRecordDTO record,String hash) {
        ExchangeRateDTO v=record.exchangeRate();ExchangeRateDO t=new ExchangeRateDO();
        t.setSourceSystem("SAP");t.setSourceId(record.sourceId());t.setRateType(v.rateType());
        t.setFromCurrency(v.fromCurrency());t.setToCurrency(v.toCurrency());t.setEffectiveDate(v.effectiveDate());
        t.setRawRate(v.rawRate());t.setFromFactor(v.fromFactor());t.setToFactor(v.toFactor());
        t.setQuotationMethod(v.quotationMethod());t.setNormalizedRate(v.normalizedRate());
        t.setBlocked(v.blocked());t.setEnabled(v.enabled());t.setIsDeleted(v.isDeleted());
        t.setSourceUpdatedAt(record.updatedAt());t.setPayloadHash(hash);return t;
    }
    public static SourceRecordDTO source(CostCenterDO v,MasterDataCodec codec) {
        CostCenterDTO d=new CostCenterDTO(v.getControllingArea(),v.getCostCenterCode(),v.getCompanyCode(),v.getName(),
                v.getValidFrom(),v.getValidTo(),Boolean.TRUE.equals(v.getBlocked()),Boolean.TRUE.equals(v.getEnabled()),
                Boolean.TRUE.equals(v.getIsDeleted()));
        return new SourceRecordDTO(MasterDataType.COST_CENTER,codec.businessKey(d),v.getSourceId(),v.getSourceUpdatedAt(),d,null);
    }
    public static SourceRecordDTO source(ExchangeRateDO v,MasterDataCodec codec) {
        ExchangeRateDTO d=new ExchangeRateDTO(v.getRateType(),v.getFromCurrency(),v.getToCurrency(),v.getEffectiveDate(),
                v.getRawRate(),v.getFromFactor(),v.getToFactor(),v.getQuotationMethod(),v.getNormalizedRate(),
                Boolean.TRUE.equals(v.getBlocked()),Boolean.TRUE.equals(v.getEnabled()),Boolean.TRUE.equals(v.getIsDeleted()));
        return new SourceRecordDTO(MasterDataType.EXCHANGE_RATE,codec.businessKey(d),v.getSourceId(),v.getSourceUpdatedAt(),null,d);
    }
}
