package com.tenahub.bot.service;

import com.tenahub.bot.entity.Pharmacy;

import java.util.List;

public interface FavoritePharmacyService {

    void addFavorite(Long userId, Long pharmacyId);

    void removeFavorite(Long userId, Long pharmacyId);

    boolean isFavorite(Long userId, Long pharmacyId);

    List<Pharmacy> getFavorites(Long userId);
}