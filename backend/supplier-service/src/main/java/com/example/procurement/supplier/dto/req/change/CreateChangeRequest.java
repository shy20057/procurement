package com.example.procurement.supplier.dto.req.change;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class CreateChangeRequest {
    @NotBlank private String supplierCode;
    @NotNull private String changeType;
    private String changeReason;
    @NotBlank @Size(max = 50) private String applicant;
    private List<ChangeFieldEntry> entries;

    @Data
    public static class ChangeFieldEntry {
        private Integer seq;
        @NotBlank private String fieldName;
        private String oldValue;
        @NotBlank private String newValue;
    }
}
