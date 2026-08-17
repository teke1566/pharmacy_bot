package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStatusHistoryDTO {
    private Long historyId;
    private Long reservationId;
    private String fromStatus;
    private String toStatus;
    private Long actorTelegramId;
    private String reason;
    private LocalDateTime createdAt;
}
