package com.example.procurement.supplier.common.errorcode;

import com.example.procurement.supplier.common.result.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SrmErrorCode implements IErrorCode {
    // 通用错误
    SYSTEM_ERROR("SRM_001000", "系统异常，请稍后重试"),
    VALIDATION_ERROR("SRM_001001", "请求参数错误"),
    NOT_FOUND("SRM_001002", "资源不存在"),
    BUSINESS_ERROR("SRM_001003", "业务处理失败"),
    METHOD_NOT_ALLOWED("SRM_001004", "不支持的请求方法"),

    // D1 邀请单
    INVITE_NOT_FOUND("SRM_002001", "邀请单不存在"),
    INVITE_DUPLICATE("SRM_002002", "该企业存在待处理的邀请单"),
    INVITE_NOT_PENDING("SRM_002003", "只有待响应状态的邀请单可以操作"),

    // D2 注册单
    REG_NOT_FOUND("SRM_003001", "注册单不存在"),
    REG_NOT_DRAFT("SRM_003002", "只有草稿状态可以提交"),
    REG_NOT_SUBMITTED("SRM_003003", "只有已提交状态可以下推"),

    // D3 认证单
    CERT_NOT_FOUND("SRM_004001", "认证单不存在"),
    CERT_NOT_DRAFT("SRM_004002", "只有草稿状态可以提交"),
    CERT_NOT_IN_REVIEW("SRM_004003", "只有在审核中状态可以审核"),
    CERT_NOT_APPROVED("SRM_004004", "只有已审核状态可以反审核"),

    // D4 档案
    ARCHIVE_NOT_FOUND("SRM_005001", "供应商档案不存在"),
    ARCHIVE_DUPLICATE_CODE("SRM_005002", "供应商编码已存在"),

    // D5 变更单
    CHANGE_NOT_FOUND("SRM_006001", "变更单不存在"),
    CHANGE_NOT_DRAFT("SRM_006002", "只有草稿状态可以提交"),
    CHANGE_NOT_IN_REVIEW("SRM_006003", "只有在审核中状态可以审核"),
    CHANGE_NOT_APPROVED("SRM_006004", "只有已审核状态可以反审核"),

    // D6 冻结/解冻单
    FREEZE_NOT_FOUND("SRM_007001", "冻结/解冻单不存在"),
    FREEZE_NOT_DRAFT("SRM_007002", "只有草稿状态可以提交"),
    FREEZE_NOT_IN_REVIEW("SRM_007003", "只有在审核中状态可以审核"),
    FREEZE_NOT_APPROVED("SRM_007004", "只有已审核状态可以反审核"),
    FREEZE_ARCHIVE_NOT_FOUND("SRM_007005", "供应商档案不存在，无法执行冻结/解冻"),

    // D7 黑名单
    BLACKLIST_NOT_FOUND("SRM_008001", "黑名单单不存在"),
    BLACKLIST_NOT_DRAFT("SRM_008002", "只有草稿状态可以提交"),
    BLACKLIST_NOT_IN_REVIEW("SRM_008003", "只有在审核中状态可以审核"),
    BLACKLIST_NOT_APPROVED("SRM_008004", "只有已审核状态可以反审核"),

    // D8 绩效考核
    PERF_NOT_FOUND("SRM_009001", "绩效考核单不存在"),
    PERF_NOT_DRAFT("SRM_009002", "只有草稿状态可以提交"),
    PERF_NOT_SUBMITTED("SRM_009003", "只有已提交状态可以确认"),
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
