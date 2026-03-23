package com.tenahub.bot.repository;

import com.tenahub.bot.entity.UserFavoritePharmacy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFavoritePharmacyRepository extends JpaRepository<UserFavoritePharmacy, Long> {

    boolean existsByUserIdAndPharmacyId(Long userId, Long pharmacyId);

    Optional<UserFavoritePharmacy> findByUserIdAndPharmacyId(Long userId, Long pharmacyId);

    List<UserFavoritePharmacy> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndPharmacyId(Long userId, Long pharmacyId);
}