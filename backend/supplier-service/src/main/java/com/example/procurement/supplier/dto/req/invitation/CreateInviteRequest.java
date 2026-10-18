package com.example.procurement.supplier.dto.req.invitation;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateInviteRequest {
    @NotBlank @Size(max = 100) private String inviteeName;
    private String inviteeCreditCode;
    private String inviteeContact;
    private String inviteePhone;
    private String inviteeEmail;
    @NotBlank @Size(max = 50) private String invitedBy;
    @NotNull private LocalDate expireDate;
    private List<CategoryEntry> categories;

    @Data
    public static class CategoryEntry {
        private Integer seq;
        @NotBlank private String categoryCode;
        @NotBlank private String categoryName;
        private String remark;
    }
}
