package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByPharmacyIdAndInventoryIdOrderByCreatedAtDesc(Long pharmacyId, Long inventoryId);

    List<PriceHistory> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);

    long countByPharmacyIdAndCreatedAtGreaterThanEqual(Long pharmacyId, java.time.LocalDateTime from);
}
