package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MedicineInfoDTO;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicineKnowledgeServiceImplTest {

    private final MedicineKnowledgeServiceImpl service = new MedicineKnowledgeServiceImpl();

    @Test
    void lookup_findsCanonicalName() {
        Optional<MedicineInfoDTO> info = service.lookup("Paracetamol");

        assertTrue(info.isPresent());
        assertEquals("Paracetamol", info.get().getName());
        assertTrue(info.get().getSafetyNote().contains("Consult a pharmacist"));
    }

    @Test
    void lookup_resolvesAlias() {
        Optional<MedicineInfoDTO> info = service.lookup("panadol");

        assertTrue(info.isPresent());
        assertEquals("Paracetamol", info.get().getName());
    }

    @Test
    void lookup_returnsEmptyForUnknown() {
        assertTrue(service.lookup("unknown-drug-xyz").isEmpty());
        assertTrue(service.lookup(null).isEmpty());
    }

    @Test
    void detectMedicineName_findsNameInSentence() {
        assertEquals("paracetamol", service.detectMedicineName("I need paracetamol today"));
        assertEquals("paracetamol", service.detectMedicineName("Do you have Panadol?"));
        assertEquals(null, service.detectMedicineName("hello there"));
    }
}
