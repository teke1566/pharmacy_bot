package com.tenahub.bot.service;

import com.tenahub.bot.dto.MedicineInfoDTO;

import java.util.Optional;

public interface MedicineKnowledgeService {

    /**
     * Look up structured medicine information by normalized name or alias.
     *
     * @param medicineName the medicine name (case-insensitive)
     * @return an Optional containing the medicine info, or empty if not found
     */
    Optional<MedicineInfoDTO> lookup(String medicineName);

    /**
     * Scan a user message and return the first recognized medicine name
     * (canonical form), or null if none detected.
     *
     * @param message the raw or normalized user message
     * @return canonical medicine name, or null
     */
    String detectMedicineName(String message);
}
