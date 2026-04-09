package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for individual pharmacy photo in Mini App response.
 * 
 * Note: fileId is a Telegram file_id token, not a direct image URL.
 * For displaying images in the Mini App, you will need to:
 * 1. Exchange fileId for an actual download URL via Telegram Bot API
 * 2. Or implement a separate backend endpoint that serves the image by converting file_id to URL
 * 
 * Future implementation: Add a fileUrl field once URL resolution service is added.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppPhotoDTO {
    private Long photoId;
    
    /**
     * Telegram file_id - NOT a direct URL.
     * Required to fetch the actual image via Telegram Bot API.
     */
    private String fileId;
    
    private Boolean mainPhoto;
    
    private Integer sortOrder;
    
    private String caption;
}
