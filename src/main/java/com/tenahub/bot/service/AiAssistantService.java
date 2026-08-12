package com.tenahub.bot.service;

import com.tenahub.bot.dto.AiChatDebugResponseDTO;
import com.tenahub.bot.dto.AiChatRequestDTO;
import com.tenahub.bot.dto.AiChatResponseDTO;

public interface AiAssistantService {
    AiChatResponseDTO chat(AiChatRequestDTO request,
                           Long headerUserTelegramId,
                           Long headerPharmacyTelegramId,
                           Long headerAdminTelegramId);

    AiChatDebugResponseDTO chatDebug(AiChatRequestDTO request,
                                     Long headerUserTelegramId,
                                     Long headerPharmacyTelegramId,
                                     Long headerAdminTelegramId);
}
