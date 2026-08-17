package com.tenahub.bot.repository;

import com.tenahub.bot.entity.StockMovement;
import com.tenahub.bot.entity.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByInventoryIdOrderByCreatedAtDescIdDesc(Long inventoryId);

    List<StockMovement> findByReservationIdAndMovementType(Long reservationId, StockMovementType movementType);
}
