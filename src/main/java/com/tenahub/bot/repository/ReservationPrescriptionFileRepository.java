package com.tenahub.bot.repository;

import com.tenahub.bot.entity.ReservationPrescriptionFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationPrescriptionFileRepository extends JpaRepository<ReservationPrescriptionFile, Long> {

    interface PrescriptionFileRef {
        Long getId();
        Long getReservationId();
        String getReservationGroupId();
    }

    List<ReservationPrescriptionFile> findByReservationIdOrderByUploadedAtAsc(Long reservationId);

    List<ReservationPrescriptionFile> findByReservationIdInOrderByUploadedAtAsc(List<Long> reservationIds);

    List<ReservationPrescriptionFile> findByReservationGroupIdOrderByUploadedAtAsc(String reservationGroupId);

    /** Metadata only — avoids loading PostgreSQL OID LOB bytes into the pharmacy list response. */
    @Query("""
            SELECT f.id AS id, f.reservationId AS reservationId, f.reservationGroupId AS reservationGroupId
            FROM ReservationPrescriptionFile f
            WHERE f.reservationId IN :reservationIds
            ORDER BY f.uploadedAt ASC
            """)
    List<PrescriptionFileRef> findRefsByReservationIdIn(@Param("reservationIds") List<Long> reservationIds);

    @Query("""
            SELECT f.id AS id, f.reservationId AS reservationId, f.reservationGroupId AS reservationGroupId
            FROM ReservationPrescriptionFile f
            WHERE f.reservationGroupId = :groupId
            ORDER BY f.uploadedAt ASC
            """)
    List<PrescriptionFileRef> findRefsByReservationGroupId(@Param("groupId") String groupId);
}