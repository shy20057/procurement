package com.example.procurement.supplier.dto.resp.invitation;

import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
public class InviteResponse {
    private Long id;
    private String billNo;
    private String inviteeName;
    private String inviteeCreditCode;
    private String inviteeContact;
    private String inviteePhone;
    private String inviteeEmail;
    private String invitedBy;
    private LocalDate inviteDate;
    private LocalDate expireDate;
    private String status;
    private String relatedRegBillNo;
    private List<CategoryEntry> categories;
    private Instant createdAt;

    @Data
    public static class CategoryEntry {
        private Integer seq;
        private String categoryCode;
        private String categoryName;
        private String remark;
    }
}
