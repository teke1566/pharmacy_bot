package com.tenahub.bot.repository;

import com.tenahub.bot.entity.Promotion;
import com.tenahub.bot.entity.PromotionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);

    Optional<Promotion> findByIdAndPharmacyId(Long id, Long pharmacyId);

    List<Promotion> findByPharmacyIdAndInventoryIdAndStatus(Long pharmacyId, Long inventoryId, PromotionStatus status);

    List<Promotion> findByStatusAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
            PromotionStatus status, LocalDateTime startBound, LocalDateTime endBound);

    List<Promotion> findByStatusAndEndAtLessThan(PromotionStatus status, LocalDateTime at);

    long countByPharmacyIdAndStatus(Long pharmacyId, PromotionStatus status);
}
