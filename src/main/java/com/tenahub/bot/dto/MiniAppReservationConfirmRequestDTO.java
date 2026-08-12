package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppReservationConfirmRequestDTO {
    private Long telegramUserId;
    private Long pharmacyId;
    private Long medicineId;
    private Integer quantity;
    private String phone;
    private String customerName;
    private String note;
    private List<MiniAppReservationConfirmItemDTO> items;
    private String telegramInitData;
    private String initData;
}