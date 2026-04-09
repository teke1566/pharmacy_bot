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
public class MiniAppMedicinePhotosDTO {

    private Long medicineId;

    private Long pharmacyId;

    private String medicineName;

    private List<MiniAppPhotoDTO> photos;
}