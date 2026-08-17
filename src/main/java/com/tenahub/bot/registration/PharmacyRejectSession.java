package com.tenahub.bot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PharmacyRejectSession {
    private PharmacyRejectType type;
    /** Reservation id for single rejects; unused for group types. */
    private Long reservationId;
    /** Group id for group rejects; unused for single reservation reject. */
    private String groupId;
}
