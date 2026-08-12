package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineSearchLog;
import com.tenahub.bot.repository.MedicineSearchLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicineSearchLogServiceImplTest {

    @Mock
    private MedicineSearchLogRepository repository;

    @InjectMocks
    private MedicineSearchLogServiceImpl service;

    @Test
    void logSearch_skipsBlankMedicine() {
        service.logSearch(1L, "  ");

        verify(repository, never()).save(any());
    }

    @Test
    void logSearch_normalizesAndSaves() {
        service.logSearch(3L, "Panadol");

        ArgumentCaptor<MedicineSearchLog> captor = ArgumentCaptor.forClass(MedicineSearchLog.class);
        verify(repository).save(captor.capture());
        assertEquals(3L, captor.getValue().getUserId());
        assertEquals("paracetamol", captor.getValue().getMedicineName());
    }

    @Test
    void getRecentSearches_returnsDistinctLimitFive() {
        when(repository.findTop10ByUserIdOrderBySearchedAtDesc(3L)).thenReturn(List.of(
                log("paracetamol"),
                log("paracetamol"),
                log("ibuprofen"),
                log(" "),
                log("amoxicillin")
        ));

        List<String> recent = service.getRecentSearches(3L);

        assertEquals(List.of("paracetamol", "ibuprofen", "amoxicillin"), recent);
    }

    private static MedicineSearchLog log(String name) {
        MedicineSearchLog item = new MedicineSearchLog();
        item.setMedicineName(name);
        return item;
    }
}
