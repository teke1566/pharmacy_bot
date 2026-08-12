package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicinePhoto;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.MedicinePhotoRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicinePhotoServiceImplTest {

    @Mock
    private MedicinePhotoRepository medicinePhotoRepository;
    @Mock
    private PharmacyInventoryRepository pharmacyInventoryRepository;
    @Mock
    private RestTemplate restTemplate;

    private MedicinePhotoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MedicinePhotoServiceImpl(medicinePhotoRepository, pharmacyInventoryRepository, restTemplate);
        ReflectionTestUtils.setField(service, "botToken", "token");
        ReflectionTestUtils.setField(service, "baseApiUrl", "https://api.telegram.org");
    }

    @Test
    void addPhoto_firstPhotoIsMain() {
        when(pharmacyInventoryRepository.findById(10L))
                .thenReturn(Optional.of(PharmacyInventory.builder().id(10L).build()));
        when(medicinePhotoRepository.findByMedicineIdOrderByMainPhotoDescSortOrderAscIdAsc(10L))
                .thenReturn(List.of());
        when(medicinePhotoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MedicinePhoto saved = service.addPhoto(10L, "file-1", "front");

        assertTrue(saved.isMainPhoto());
        assertEquals(1, saved.getSortOrder());
        assertEquals("file-1", saved.getTelegramFileId());
    }

    @Test
    void addPhoto_rejectsWhenMaxReached() {
        when(pharmacyInventoryRepository.findById(10L))
                .thenReturn(Optional.of(PharmacyInventory.builder().id(10L).build()));
        when(medicinePhotoRepository.findByMedicineIdOrderByMainPhotoDescSortOrderAscIdAsc(10L))
                .thenReturn(List.of(
                        MedicinePhoto.builder().id(1L).build(),
                        MedicinePhoto.builder().id(2L).build(),
                        MedicinePhoto.builder().id(3L).build(),
                        MedicinePhoto.builder().id(4L).build()
                ));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.addPhoto(10L, "file-5", null));
        assertEquals("Maximum 4 medicine photos allowed", error.getMessage());
    }

    @Test
    void addPhoto_requiresMedicineId() {
        assertThrows(RuntimeException.class, () -> service.addPhoto(null, "file", null));
    }

    @Test
    void getImageBytesByTelegramFileId_downloadsFromTelegram() {
        when(restTemplate.getForObject(eq("https://api.telegram.org/bottoken/getFile?file_id=abc"), eq(Map.class)))
                .thenReturn(Map.of("result", Map.of("file_path", "photos/a.jpg")));
        when(restTemplate.getForObject(eq("https://api.telegram.org/file/bottoken/photos/a.jpg"), eq(byte[].class)))
                .thenReturn(new byte[]{1, 2, 3});

        byte[] bytes = service.getImageBytesByTelegramFileId("abc");

        assertArrayEquals(new byte[]{1, 2, 3}, bytes);
    }

    @Test
    void getImageBytesByTelegramFileId_rejectsBlank() {
        assertThrows(RuntimeException.class, () -> service.getImageBytesByTelegramFileId(" "));
    }
}
