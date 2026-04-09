package com.tenahub.bot.repository;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MedicineReservationRepository extends JpaRepository<MedicineReservation, Long> {

    List<MedicineReservation> findByPharmacyIdAndStatus(Long pharmacyId, MedicineReservationStatus status);

    List<MedicineReservation> findByUserId(Long userId);

    List<MedicineReservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<MedicineReservation> findByUserIdAndStatusIn(
            Long userId,
            List<MedicineReservationStatus> statuses
    );

    List<MedicineReservation> findByUserIdAndStatusInOrderByCreatedAtDesc(
            Long userId,
            List<MedicineReservationStatus> statuses
    );

    List<MedicineReservation> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);
    long countByPharmacyId(Long pharmacyId);
    long countByPharmacyIdAndStatus(Long pharmacyId, MedicineReservationStatus status);

    List<MedicineReservation> findByStatusOrderByCreatedAtDesc(MedicineReservationStatus status);

    List<MedicineReservation> findTop10ByStatusOrderByCreatedAtDesc(MedicineReservationStatus status);

    List<MedicineReservation> findTop10ByOrderByCreatedAtDesc();

    List<MedicineReservation> findByStatusAndExpiresAtBefore(
            MedicineReservationStatus status,
            LocalDateTime time
    );

    List<MedicineReservation> findByStatusAndCreatedAtBefore(
            MedicineReservationStatus status,
            LocalDateTime time
    );

    List<MedicineReservation> findByStatusAndExpiresAtBetweenAndReminderSentFalse(
            MedicineReservationStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    long countByUserIdAndStatusIn(Long userId, List<MedicineReservationStatus> statuses);

    long countByStatus(MedicineReservationStatus status);

    long countByCreatedAtAfter(LocalDateTime time);

    Page<MedicineReservation> findByStatusOrderByCreatedAtDesc(MedicineReservationStatus status, Pageable pageable);

    @Query("""
           select r.medicineName, count(r)
           from MedicineReservation r
           group by r.medicineName
           order by count(r) desc
           """)
    List<Object[]> findTopRequestedMedicines();

    List<MedicineReservation> findByReservationGroupId(String reservationGroupId);

        List<MedicineReservation> findByReservationGroupIdOrderByCreatedAtDesc(String reservationGroupId);

    List<MedicineReservation> findByReservationGroupIdAndStatus(String reservationGroupId, MedicineReservationStatus status);

        Optional<MedicineReservation> findByQrToken(String qrToken);

        List<MedicineReservation> findAllByQrToken(String qrToken);
    
}