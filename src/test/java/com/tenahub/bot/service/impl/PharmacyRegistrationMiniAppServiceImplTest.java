package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyMiniAppRegistrationStatusDTO;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.service.RegistrationService;
import com.tenahub.bot.util.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyRegistrationMiniAppServiceImplTest {

    @Mock
    private RegistrationService registrationService;
    @Mock
    private TelegramClient telegramClient;

    private PharmacyRegistrationMiniAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PharmacyRegistrationMiniAppServiceImpl(registrationService, telegramClient);
        ReflectionTestUtils.setField(service, "adminChatId", 99L);
    }

    @Test
    void getStatus_approvedPharmacy() {
        when(registrationService.isRegisteredPharmacy(7L)).thenReturn(true);

        PharmacyMiniAppRegistrationStatusDTO status = service.getStatus(7L);

        assertEquals("approved", status.getStatus());
    }

    @Test
    void getStatus_pending() {
        when(registrationService.isRegisteredPharmacy(7L)).thenReturn(false);
        when(registrationService.getLatest(7L)).thenReturn(PharmacyRegistration.builder()
                .id(3L)
                .status("PENDING")
                .build());

        PharmacyMiniAppRegistrationStatusDTO status = service.getStatus(7L);

        assertEquals("pending", status.getStatus());
        assertEquals(3L, status.getRegistrationId());
    }

    @Test
    void submit_rejectsInvalidPhone() {
        when(registrationService.isRegisteredPharmacy(7L)).thenReturn(false);
        when(registrationService.exists(7L)).thenReturn(false);
        MockMultipartFile license = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[] {1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> service.submit(
                7L, "Bole", "Addis Ababa", "Bole", "abc", "Paracetamol",
                "8:00", "20:00", 8.9, 38.7, null, null, null,
                LocalDate.now().plusYears(1).toString(), license));
        verify(registrationService, never()).register(any(), any(), any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void submit_savesAndNotifiesAdmin() {
        when(registrationService.isRegisteredPharmacy(7L)).thenReturn(false);
        when(registrationService.exists(7L)).thenReturn(false);
        when(registrationService.register(any(), any(), any(), any(), any(), any(), any(), eq(7L))).thenReturn(44L);
        when(registrationService.getRegistration(44L)).thenReturn(PharmacyRegistration.builder()
                .id(44L)
                .name("Bole")
                .city("Addis Ababa")
                .area("Bole")
                .phone("+251911000000")
                .medicines("Paracetamol")
                .openTime("8:00")
                .closeTime("20:00")
                .latitude(8.9)
                .longitude(38.7)
                .licenseExpiryDate(LocalDate.now().plusYears(1))
                .telegramId(7L)
                .build());
        when(telegramClient.displayLocation(eq(99L), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(telegramClient.sendPhotoBytesWithButtons(eq(99L), any(), anyString(), anyString(), eq(44L)))
                .thenReturn("file-1");
        MockMultipartFile license = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[] {1, 2, 3});

        PharmacyMiniAppRegistrationStatusDTO result = service.submit(
                7L, "Bole", "Addis Ababa", "Bole", "+251911000000", "Paracetamol",
                "8:00", "20:00", 8.9, 38.7, "Bole", "near mall", null,
                LocalDate.now().plusYears(1).toString(), license);

        assertEquals("pending", result.getStatus());
        assertEquals(44L, result.getRegistrationId());
        verify(registrationService).saveLicense(7L, "file-1");
        verify(telegramClient).sendPhotoBytesWithButtons(eq(99L), any(), eq("license.jpg"), anyString(), eq(44L));
    }

    @Test
    void submit_conflictWhenAlreadyPending() {
        when(registrationService.isRegisteredPharmacy(7L)).thenReturn(false);
        when(registrationService.exists(7L)).thenReturn(true);
        MockMultipartFile license = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[] {1, 2, 3});

        assertThrows(IllegalStateException.class, () -> service.submit(
                7L, "Bole", "Addis Ababa", "Bole", "+251911000000", "Paracetamol",
                "8:00", "20:00", 8.9, 38.7, null, null, null,
                LocalDate.now().plusYears(1).toString(), license));
    }
}
