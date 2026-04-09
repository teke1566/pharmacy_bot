package com.tenahub.bot.repository;

import com.tenahub.bot.entity.MedicineAvailabilityAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicineAvailabilityAlertRepository extends JpaRepository<MedicineAvailabilityAlert, Long> {

    List<MedicineAvailabilityAlert> findByUserIdAndActiveTrue(Long userId);

    List<MedicineAvailabilityAlert> findByActiveTrueAndMedicineNameIgnoreCase(String medicineName);

    Optional<MedicineAvailabilityAlert> findByUserIdAndMedicineNameIgnoreCaseAndActiveTrue(Long userId, String medicineName);

    Optional<MedicineAvailabilityAlert> findTopByUserIdAndMedicineNameIgnoreCaseOrderByIdDesc(Long userId, String medicineName);

    long countByUserIdAndActiveTrue(Long userId);
}