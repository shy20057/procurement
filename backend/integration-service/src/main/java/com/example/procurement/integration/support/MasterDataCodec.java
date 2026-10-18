package com.example.procurement.integration.support;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.CostCenterDTO;
import com.example.procurement.integration.dto.domain.ExchangeRateDTO;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.exception.IntegrationProblem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class MasterDataCodec {
    private final ObjectMapper mapper;
    public MasterDataCodec(ObjectMapper mapper) { this.mapper = mapper; }

    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("无法序列化同步状态", e); }
    }
    public <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException e) {
            throw new IntegrationProblem("INVALID_PAYLOAD", "源数据或检查点结构不合法", false, 422);
        }
    }
    public String hash(SourceRecordDTO record) {
        Object data = record.costCenter();
        if (record.entityType() == MasterDataType.EXCHANGE_RATE) {
            ExchangeRateDTO clean = validate(record.exchangeRate());
            BigDecimal stored = record.exchangeRate().normalizedRate();
            data = new ExchangeRateDTO(clean.rateType(), clean.fromCurrency(), clean.toCurrency(), clean.effectiveDate(),
                    clean.rawRate(), clean.fromFactor(), clean.toFactor(), clean.quotationMethod(),
                    stored == null ? null : stored.setScale(12, RoundingMode.UNNECESSARY), clean.deleted());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(data).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public CostCenterDTO validate(CostCenterDTO value) {
        require(value != null, "缺少成本中心数据");
        key(value.controllingArea(), 20); key(value.costCenterCode(), 40); key(value.companyCode(), 20);
        require(value.name() != null && !value.name().isBlank() && value.name().length() <= 200, "成本中心名称不能为空且不超过200字");
        require(value.validFrom() != null && value.validTo() != null
                && !value.validFrom().isAfter(value.validTo()), "成本中心有效期不合法");
        return value;
    }
    public ExchangeRateDTO validate(ExchangeRateDTO value) {
        require(value != null, "缺少汇率数据");
        key(value.rateType(), 20);
        require(value.fromCurrency() != null && value.fromCurrency().matches("[A-Z]{3}"), "原币必须为三位大写币种");
        require(value.toCurrency() != null && value.toCurrency().matches("[A-Z]{3}"), "目标币必须为三位大写币种");
        require(!value.fromCurrency().equals(value.toCurrency()), "原币与目标币不能相同");
        require(value.effectiveDate() != null, "缺少汇率生效日期");
        decimal(value.rawRate(), 28, 12); decimal(value.fromFactor(), 19, 4); decimal(value.toFactor(), 19, 4);
        require("DIRECT".equals(value.quotationMethod()) || "INDIRECT".equals(value.quotationMethod()), "报价方式必须为DIRECT或INDIRECT");
        // This is the explicitly defined demo contract, not an assumption about SAP UKURS.
        BigDecimal denominator = value.fromFactor();
        BigDecimal numerator = value.rawRate().multiply(value.toFactor());
        if ("INDIRECT".equals(value.quotationMethod())) {
            numerator = value.toFactor();
            denominator = value.rawRate().multiply(value.fromFactor());
        }
        BigDecimal normalized = numerator.divide(denominator, 12, RoundingMode.HALF_UP);
        decimal(normalized, 28, 12);
        return new ExchangeRateDTO(value.rateType(), value.fromCurrency(), value.toCurrency(),
                value.effectiveDate(), value.rawRate().stripTrailingZeros(), value.fromFactor().stripTrailingZeros(),
                value.toFactor().stripTrailingZeros(), value.quotationMethod(), normalized, value.deleted());
    }
    public String businessKey(CostCenterDTO value) { return value.controllingArea() + "|" + value.costCenterCode(); }
    public String businessKey(ExchangeRateDTO value) {
        return value.rateType() + "|" + value.fromCurrency() + "|" + value.toCurrency() + "|" + value.effectiveDate();
    }
    public void validate(SourceRecordDTO record, MasterDataType type) {
        require(record != null && record.entityType() == type, "源记录类型不匹配");
        require(record.sourceVersion() > 0 && record.changedAt() != null
                && !record.changedAt().isAfter(Instant.now().plusSeconds(300)), "源版本或变更时间不合法");
        String expected;
        if (type == MasterDataType.COST_CENTER) {
            expected = businessKey(validate(record.costCenter()));
            require(record.exchangeRate() == null, "成本中心记录不应携带汇率数据");
        } else {
            ExchangeRateDTO normalized = validate(record.exchangeRate());
            require(record.costCenter() == null && record.exchangeRate().normalizedRate() != null
                    && normalized.normalizedRate().compareTo(record.exchangeRate().normalizedRate()) == 0,
                    "汇率规范化值与报价契约不一致");
            expected = businessKey(normalized);
        }
        require(expected.equals(record.businessKey()), "源业务键与数据内容不匹配");
    }
    public void require(boolean condition, String message) {
        if (!condition) throw new IntegrationProblem("VALIDATION_ERROR", message, false, 422);
    }
    private void key(String value, int length) {
        require(value != null && value.matches("[A-Za-z0-9_-]{1," + length + "}"), "编码仅支持字母、数字、下划线和短横线");
    }
    private void decimal(BigDecimal value, int precision, int scale) {
        require(value != null && value.signum() > 0, "汇率和换算因子必须大于0");
        BigDecimal stripped = value.stripTrailingZeros();
        require(stripped.scale() <= scale && stripped.precision() - stripped.scale() <= precision - scale,
                "汇率或因子超出允许精度");
    }
}
