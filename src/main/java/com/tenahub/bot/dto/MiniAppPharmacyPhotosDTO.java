package com.tenahub.bot.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mini App response DTO for pharmacy photos.
 * Returned by GET /api/miniapp/pharmacies/{pharmacyId}/photos
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppPharmacyPhotosDTO {
    
    private Long pharmacyId;
    
    private String pharmacyName;
    
    /**
     * Ordered list of pharmacy photos.
     * Sorted by mainPhoto (main first), then by sortOrder and ID.
     * Empty list if no photos available.
     */
    private List<MiniAppPhotoDTO> photos;
}
