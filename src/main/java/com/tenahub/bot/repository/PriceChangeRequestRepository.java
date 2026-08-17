package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PriceChangeRequest;
import com.tenahub.bot.entity.PriceChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceChangeRequestRepository extends JpaRepository<PriceChangeRequest, Long> {

    List<PriceChangeRequest> findByPharmacyIdAndStatusOrderByRequestedAtDesc(Long pharmacyId, PriceChangeRequestStatus status);

    List<PriceChangeRequest> findByPharmacyIdOrderByRequestedAtDesc(Long pharmacyId);

    Optional<PriceChangeRequest> findByIdAndPharmacyId(Long id, Long pharmacyId);

    List<PriceChangeRequest> findByStatusAndEffectiveAtLessThanEqual(PriceChangeRequestStatus status, LocalDateTime at);

    long countByPharmacyIdAndStatus(Long pharmacyId, PriceChangeRequestStatus status);
}
