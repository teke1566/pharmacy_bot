package com.tenahub.bot.repository;

import com.tenahub.bot.entity.InventoryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryHistoryRepository extends JpaRepository<InventoryHistory, Long> {

    List<InventoryHistory> findByPharmacyIdAndCreatedAtBetween(
            Long pharmacyId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<InventoryHistory> findByCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );
}