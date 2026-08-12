package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyPhoto;
import com.tenahub.bot.repository.PharmacyPhotoRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyPhotoServiceImplTest {

    @Mock
    private PharmacyPhotoRepository pharmacyPhotoRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;

    @InjectMocks
    private PharmacyPhotoServiceImpl service;

    @Test
    void addPhoto_rejectsBlankFileId() {
        assertThrows(RuntimeException.class, () -> service.addPhoto(1L, "  ", "cap"));
    }

    @Test
    void addPhoto_savesFirstPhotoAsMainAndSyncsLegacy() {
        Pharmacy pharmacy = Pharmacy.builder().id(1L).build();
        when(pharmacyPhotoRepository.findByPharmacyIdOrderByMainPhotoDescSortOrderAscIdAsc(1L))
                .thenReturn(List.of());
        when(pharmacyRepository.findById(1L)).thenReturn(Optional.of(pharmacy));
        when(pharmacyPhotoRepository.save(any())).thenAnswer(inv -> {
            PharmacyPhoto photo = inv.getArgument(0);
            if (photo.getId() == null) {
                photo.setId(11L);
            }
            return photo;
        });
        when(pharmacyPhotoRepository.findFirstByPharmacyIdAndMainPhotoTrueOrderBySortOrderAscIdAsc(1L))
                .thenReturn(Optional.of(PharmacyPhoto.builder().id(11L).fileId("file-1").mainPhoto(true).build()));

        PharmacyPhoto saved = service.addPhoto(1L, "file-1", "front");

        assertTrue(saved.isMainPhoto());
        assertEquals("file-1", saved.getFileId());
        ArgumentCaptor<Pharmacy> captor = ArgumentCaptor.forClass(Pharmacy.class);
        verify(pharmacyRepository).save(captor.capture());
        assertEquals("file-1", captor.getValue().getPhotoFileId());
    }

    @Test
    void ensureLegacyPhotoImported_skipsWhenPhotosExist() {
        when(pharmacyPhotoRepository.findByPharmacyIdOrderByMainPhotoDescSortOrderAscIdAsc(1L))
                .thenReturn(List.of(PharmacyPhoto.builder().id(2L).build()));

        service.ensureLegacyPhotoImported(1L);

        verify(pharmacyRepository, never()).findById(1L);
        verify(pharmacyPhotoRepository, never()).save(any());
    }

    @Test
    void ensureLegacyPhotoImported_importsLegacyFileId() {
        when(pharmacyPhotoRepository.findByPharmacyIdOrderByMainPhotoDescSortOrderAscIdAsc(1L))
                .thenReturn(List.of());
        when(pharmacyRepository.findById(1L)).thenReturn(Optional.of(
                Pharmacy.builder().id(1L).photoFileId("legacy-file").build()));

        service.ensureLegacyPhotoImported(1L);

        ArgumentCaptor<PharmacyPhoto> captor = ArgumentCaptor.forClass(PharmacyPhoto.class);
        verify(pharmacyPhotoRepository).save(captor.capture());
        assertEquals("legacy-file", captor.getValue().getFileId());
        assertTrue(captor.getValue().isMainPhoto());
    }
}
