package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacySupplierDTO {
    private Long supplierId;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String contactPerson;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private List<String> suppliedMedicines;
}
