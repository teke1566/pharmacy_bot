package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyRating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface PharmacyRatingRepository
        extends JpaRepository<PharmacyRating, Long> {

    // get all ratings for one pharmacy
    List<PharmacyRating> findByPharmacyId(Long pharmacyId);

    // check if a user already rated this pharmacy
    boolean existsByPharmacyIdAndUserId(Long pharmacyId, Long userId);

}