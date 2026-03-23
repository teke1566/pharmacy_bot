package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.UserFavoritePharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.UserFavoritePharmacyRepository;
import com.tenahub.bot.service.FavoritePharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoritePharmacyServiceImpl implements FavoritePharmacyService {

    private final UserFavoritePharmacyRepository favoriteRepository;
    private final PharmacyRepository pharmacyRepository;

    @Override
    public void addFavorite(Long userId, Long pharmacyId) {
        if (favoriteRepository.existsByUserIdAndPharmacyId(userId, pharmacyId)) {
            return;
        }

        pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        favoriteRepository.save(
                UserFavoritePharmacy.builder()
                        .userId(userId)
                        .pharmacyId(pharmacyId)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    @Override
     @Transactional
    public void removeFavorite(Long userId, Long pharmacyId) {
        favoriteRepository.deleteByUserIdAndPharmacyId(userId, pharmacyId);
    }

    @Override
    public boolean isFavorite(Long userId, Long pharmacyId) {
        return favoriteRepository.existsByUserIdAndPharmacyId(userId, pharmacyId);
    }

    @Override
    public List<Pharmacy> getFavorites(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(f -> pharmacyRepository.findById(f.getPharmacyId()).orElse(null))
                .filter(p -> p != null)
                .toList();
    }
}