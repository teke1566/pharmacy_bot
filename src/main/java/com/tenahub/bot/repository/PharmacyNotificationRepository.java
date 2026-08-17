package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacyNotificationRepository extends JpaRepository<PharmacyNotification, Long> {

    List<PharmacyNotification> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);

    List<PharmacyNotification> findByPharmacyIdAndReadAtIsNullOrderByCreatedAtDesc(Long pharmacyId);

    long countByPharmacyIdAndReadAtIsNull(Long pharmacyId);

    Optional<PharmacyNotification> findByIdAndPharmacyId(Long id, Long pharmacyId);
}
