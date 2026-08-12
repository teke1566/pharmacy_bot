package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyRating;
import com.tenahub.bot.repository.PharmacyRatingRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceImplTest {

    @Mock
    private PharmacyRatingRepository ratingRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;

    @InjectMocks
    private RatingServiceImpl service;

    @Test
    void ratePharmacy_savesRatingAndUpdatesAverage() {
        Pharmacy pharmacy = Pharmacy.builder().id(5L).rating(0).build();
        when(ratingRepository.findByPharmacyId(5L)).thenReturn(List.of(
                rating(4),
                rating(2)
        ));
        when(pharmacyRepository.findById(5L)).thenReturn(Optional.of(pharmacy));

        service.ratePharmacy(5L, 10L, 5);

        verify(ratingRepository).save(any(PharmacyRating.class));
        ArgumentCaptor<Pharmacy> captor = ArgumentCaptor.forClass(Pharmacy.class);
        verify(pharmacyRepository).save(captor.capture());
        assertEquals(3.0, captor.getValue().getRating());
    }

    @Test
    void hasUserRated_delegatesToRepository() {
        when(ratingRepository.existsByPharmacyIdAndUserId(5L, 10L)).thenReturn(true);

        assertTrue(service.hasUserRated(5L, 10L));
    }

    private static PharmacyRating rating(int value) {
        PharmacyRating rating = new PharmacyRating();
        rating.setRating(value);
        return rating;
    }
}
