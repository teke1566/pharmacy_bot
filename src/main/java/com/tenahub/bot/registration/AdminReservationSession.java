package com.tenahub.bot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminReservationSession {
    private Long reservationId;
    /** Status name we navigated from (e.g. "PENDING"), or null if from the overview. */
    private String sourceStatus;
}
