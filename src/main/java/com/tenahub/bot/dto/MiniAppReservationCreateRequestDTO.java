package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppReservationCreateRequestDTO {
    private Long userId;
    private Long pharmacyId;
    private String medicineName;
    private Integer quantity;
    private String customerPhone;
    private String customerName;
    private String telegramInitData;
    private String initData;
}
