package com.tenahub.bot.service;

public interface RatingService {

    void ratePharmacy(Long pharmacyId, Long userId, int rating);
    boolean hasUserRated(Long pharmacyId, Long userId);

}