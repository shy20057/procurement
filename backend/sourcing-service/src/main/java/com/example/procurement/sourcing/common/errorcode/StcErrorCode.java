package com.example.procurement.sourcing.common.errorcode;

import com.example.procurement.sourcing.common.result.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StcErrorCode implements IErrorCode {
    // 通用错误
    SYSTEM_ERROR("STC_001000", "系统异常，请稍后重试"),
    VALIDATION_ERROR("STC_001001", "请求参数错误"),
    NOT_FOUND("STC_001002", "资源不存在"),
    BUSINESS_ERROR("STC_001003", "业务处理失败"),
    METHOD_NOT_ALLOWED("STC_001004", "不支持的请求方法"),

    // D9 寻源立项单
    PROJECT_NOT_FOUND("STC_002001", "寻源立项单不存在"),
    PROJECT_NOT_DRAFT("STC_002002", "只有草稿状态可以提交"),
    PROJECT_NOT_SUBMITTED("STC_002003", "只有已提交状态可以审批"),

    // D10 招标单
    TENDER_NOT_FOUND("STC_003001", "招标单不存在"),

    // D11 询价单
    RFQ_NOT_FOUND("STC_004001", "询价单不存在"),

    // D12 定标单（C3 风控守门）
    AWARD_NOT_FOUND("STC_005001", "定标单不存在"),
    AWARD_NOT_SUBMITTED("STC_005002", "只有已提交状态可以审批定标"),
    AWARD_SUPPLIER_RISK("STC_005003", "中标供应商当前处于已冻结/黑名单状态，严禁审批定标"),

    // D13 采购合同
    CONTRACT_NOT_FOUND("STC_006001", "采购合同不存在"),

    // D14 基准价目表
    PRICE_LIST_NOT_FOUND("STC_007001", "基准价目表不存在"),

    // D15 框架协议
    BPA_NOT_FOUND("STC_008001", "框架协议不存在"),

    // D16 SR 配额单
    RATIO_NOT_FOUND("STC_009001", "SR配额单不存在"),
    ;

    private final String code;
    private final String message;

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
