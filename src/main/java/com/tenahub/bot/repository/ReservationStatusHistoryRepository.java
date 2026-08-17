package com.tenahub.bot.repository;

import com.tenahub.bot.entity.ReservationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, Long> {

    List<ReservationStatusHistory> findByReservationIdAndPharmacyIdOrderByCreatedAtAsc(Long reservationId, Long pharmacyId);
}
