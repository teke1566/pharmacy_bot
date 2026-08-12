package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.UserFavoritePharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.UserFavoritePharmacyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoritePharmacyServiceImplTest {

    @Mock
    private UserFavoritePharmacyRepository favoriteRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;

    @InjectMocks
    private FavoritePharmacyServiceImpl service;

    @Test
    void addFavorite_isNoOpWhenAlreadyFavorite() {
        when(favoriteRepository.existsByUserIdAndPharmacyId(1L, 2L)).thenReturn(true);

        service.addFavorite(1L, 2L);

        verify(pharmacyRepository, never()).findById(2L);
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addFavorite_savesWhenPharmacyExists() {
        when(favoriteRepository.existsByUserIdAndPharmacyId(1L, 2L)).thenReturn(false);
        when(pharmacyRepository.findById(2L)).thenReturn(Optional.of(Pharmacy.builder().id(2L).build()));

        service.addFavorite(1L, 2L);

        verify(favoriteRepository).save(any(UserFavoritePharmacy.class));
    }

    @Test
    void addFavorite_throwsWhenPharmacyMissing() {
        when(favoriteRepository.existsByUserIdAndPharmacyId(1L, 99L)).thenReturn(false);
        when(pharmacyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.addFavorite(1L, 99L));
    }

    @Test
    void getFavorites_skipsMissingPharmacies() {
        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                UserFavoritePharmacy.builder().userId(1L).pharmacyId(2L).build(),
                UserFavoritePharmacy.builder().userId(1L).pharmacyId(3L).build()
        ));
        when(pharmacyRepository.findById(2L)).thenReturn(Optional.of(Pharmacy.builder().id(2L).name("A").build()));
        when(pharmacyRepository.findById(3L)).thenReturn(Optional.empty());

        List<Pharmacy> favorites = service.getFavorites(1L);

        assertEquals(1, favorites.size());
        assertEquals("A", favorites.get(0).getName());
    }
}
