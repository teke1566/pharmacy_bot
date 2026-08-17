package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MiniAppPhotoDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacyPhoto;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MedicinePhotoService;
import com.tenahub.bot.service.PharmacyPhotoService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.util.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyMiniAppMediaServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;
    @Mock
    private PharmacyPhotoService pharmacyPhotoService;
    @Mock
    private MedicinePhotoService medicinePhotoService;
    @Mock
    private PharmacyService pharmacyService;
    @Mock
    private TelegramClient telegramClient;

    private PharmacyMiniAppMediaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PharmacyMiniAppMediaServiceImpl(
                pharmacyRepository,
                inventoryRepository,
                pharmacyPhotoService,
                medicinePhotoService,
                pharmacyService,
                telegramClient,
                99L);
    }

    @Test
    void submitLicenseUpdate_rejectsWhenAlreadyPending() {
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(
                Pharmacy.builder().id(3L).telegramId(9001L).licenseUpdateStatus("PENDING").build()));
        MockMultipartFile license = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[] {1, 2, 3});

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.submitLicenseUpdate(9001L, license, "2027-01-01"));

        assertEquals("A license update is already pending admin approval", error.getMessage());
        verify(telegramClient, never()).sendPhotoBytes(any(), any(), any(), any());
    }

    @Test
    void submitLicenseUpdate_rejectsMissingFile() {
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(
                Pharmacy.builder().id(3L).telegramId(9001L).build()));
        MockMultipartFile empty = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[0]);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.submitLicenseUpdate(9001L, empty, "2027-01-01"));

        assertEquals("License photo is required", error.getMessage());
    }

    @Test
    void submitLicenseUpdate_uploadsThenNotifiesAdmin() {
        Pharmacy pharmacy = Pharmacy.builder()
                .id(3L)
                .telegramId(9001L)
                .name("FevPharma")
                .city("Semera")
                .area("Center")
                .build();
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(telegramClient.sendPhotoBytes(eq(9001L), any(), eq("license.jpg"), any())).thenReturn("file-1");
        when(telegramClient.displayLocation(99L, "Semera")).thenReturn("Semera");
        when(telegramClient.displayLocation(99L, "Center")).thenReturn("Center");
        MockMultipartFile license = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[] {1, 2, 3});

        service.submitLicenseUpdate(9001L, license, "2027-01-01");

        verify(pharmacyService).savePendingLicenseUpdate(9001L, "file-1", LocalDate.parse("2027-01-01"));
        verify(telegramClient).sendPhotoWithLicenseUpdateButtons(eq(99L), eq("file-1"), any(), eq(9001L));
    }

    @Test
    void addPharmacyPhoto_storesTelegramFileId() {
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(
                Pharmacy.builder().id(3L).telegramId(9001L).build()));
        when(telegramClient.sendPhotoBytes(eq(9001L), any(), eq("shop.jpg"), any())).thenReturn("file-9");
        when(pharmacyPhotoService.addPhoto(3L, "file-9", null)).thenReturn(
                PharmacyPhoto.builder().id(8L).fileId("file-9").mainPhoto(true).sortOrder(1).build());
        MockMultipartFile file = new MockMultipartFile("file", "shop.jpg", "image/jpeg", new byte[] {9, 8, 7});

        MiniAppPhotoDTO dto = service.addPharmacyPhoto(9001L, file, null);

        assertEquals(8L, dto.getPhotoId());
        assertEquals("file-9", dto.getFileId());
    }

    @Test
    void addMedicinePhoto_rejectsItemFromAnotherPharmacy() {
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(
                Pharmacy.builder().id(3L).telegramId(9001L).build()));
        when(inventoryRepository.findById(44L)).thenReturn(Optional.of(
                PharmacyInventory.builder().id(44L).pharmacyId(99L).medicineName("insulin").build()));
        MockMultipartFile file = new MockMultipartFile("file", "med.jpg", "image/jpeg", new byte[] {1});

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.addMedicinePhoto(9001L, 44L, file, null));

        assertEquals("Medicine does not belong to this pharmacy", error.getMessage());
    }

    @Test
    void listPharmacyPhotos_importsLegacyThenMaps() {
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(
                Pharmacy.builder().id(3L).telegramId(9001L).name("FevPharma").build()));
        when(pharmacyPhotoService.listByPharmacyId(3L)).thenReturn(List.of(
                PharmacyPhoto.builder().id(1L).fileId("a").mainPhoto(true).sortOrder(1).build()));

        var dto = service.listPharmacyPhotos(9001L);

        assertEquals(3L, dto.getPharmacyId());
        assertEquals(1, dto.getPhotos().size());
        verify(pharmacyPhotoService).ensureLegacyPhotoImported(3L);
    }
}
