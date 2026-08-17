package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkPricePreviewRequestDTO {
    private List<Long> itemIds;
    private String mode;
    private BigDecimal value;
    private String reason;
    private LocalDateTime effectiveAt;
}
