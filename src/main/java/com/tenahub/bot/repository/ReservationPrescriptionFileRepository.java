package com.tenahub.bot.repository;

import com.tenahub.bot.entity.ReservationPrescriptionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationPrescriptionFileRepository extends JpaRepository<ReservationPrescriptionFile, Long> {

    List<ReservationPrescriptionFile> findByReservationIdOrderByUploadedAtAsc(Long reservationId);

    List<ReservationPrescriptionFile> findByReservationIdInOrderByUploadedAtAsc(List<Long> reservationIds);

    List<ReservationPrescriptionFile> findByReservationGroupIdOrderByUploadedAtAsc(String reservationGroupId);
}