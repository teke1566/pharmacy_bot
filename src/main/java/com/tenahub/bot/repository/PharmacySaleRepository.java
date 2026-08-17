package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacySale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PharmacySaleRepository extends JpaRepository<PharmacySale, Long> {

    Optional<PharmacySale> findByReservationId(Long reservationId);

    List<PharmacySale> findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long pharmacyId, LocalDateTime start, LocalDateTime end);

    List<PharmacySale> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);
}
