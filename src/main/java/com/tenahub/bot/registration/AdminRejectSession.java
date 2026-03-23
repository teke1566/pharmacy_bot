package com.tenahub.bot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminRejectSession {
    private AdminRejectType type;
    private Long targetId; // registrationId OR pharmacyTelegramId
}