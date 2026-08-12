package com.tenahub.bot.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppReservationPreloadResponseDTO {
    private Long pharmacyId;
    private String pharmacyName;
    private List<MiniAppReservationPreloadItemDTO> items;
    private List<Long> invalidMedicineIds;
}