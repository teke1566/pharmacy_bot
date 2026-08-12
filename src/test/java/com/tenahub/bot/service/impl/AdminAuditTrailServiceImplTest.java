package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.AdminAuditTrail;
import com.tenahub.bot.repository.AdminAuditTrailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditTrailServiceImplTest {

    @Mock
    private AdminAuditTrailRepository adminAuditTrailRepository;

    @InjectMocks
    private AdminAuditTrailServiceImpl service;

    @Test
    void record_skipsWhenAdminTelegramIdMissing() {
        service.record("approve", "pharmacy", 1L, null, "details");

        verify(adminAuditTrailRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void record_normalizesBlankActionTypeToUnknown() {
        service.record("  ", "pharmacy", 9L, 55L, "  ok  ");

        ArgumentCaptor<AdminAuditTrail> captor = ArgumentCaptor.forClass(AdminAuditTrail.class);
        verify(adminAuditTrailRepository).save(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getActionType());
        assertEquals("PHARMACY", captor.getValue().getTargetEntityType());
        assertEquals("ok", captor.getValue().getDetails());
        assertEquals(55L, captor.getValue().getAdminTelegramId());
    }

    @Test
    void listRecent_delegatesToRepository() {
        when(adminAuditTrailRepository.findTop30ByOrderByActionTimestampDesc()).thenReturn(List.of());

        assertEquals(List.of(), service.listRecent());
        verify(adminAuditTrailRepository).findTop30ByOrderByActionTimestampDesc();
    }
}
