package com.tenahub.bot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSession {
    private UpdateField field;
    private Integer tempHour;
    private String openTime;
    private String closeTime;
}