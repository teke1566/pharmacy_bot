package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyRating;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacyRatingRepository;
import com.tenahub.bot.service.RatingService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final PharmacyRatingRepository ratingRepository;
    private final PharmacyRepository pharmacyRepository;

    @Override
    public void ratePharmacy(Long pharmacyId, Long userId, int rating){

        PharmacyRating r = new PharmacyRating();

        r.setPharmacyId(pharmacyId);
        r.setUserId(userId);
        r.setRating(rating);
        r.setCreatedAt(LocalDateTime.now());

        ratingRepository.save(r);

        updateAverageRating(pharmacyId);
    }

    private void updateAverageRating(Long pharmacyId){

        List<PharmacyRating> ratings =
                ratingRepository.findByPharmacyId(pharmacyId);

        double avg =
                ratings.stream()
                        .mapToInt(PharmacyRating::getRating)
                        .average()
                        .orElse(0);

        Pharmacy pharmacy =
                pharmacyRepository.findById(pharmacyId).orElseThrow();

        pharmacy.setRating(avg);

        pharmacyRepository.save(pharmacy);
    }
    @Override
public boolean hasUserRated(Long pharmacyId, Long userId){

    return ratingRepository
            .existsByPharmacyIdAndUserId(pharmacyId, userId);
}
}