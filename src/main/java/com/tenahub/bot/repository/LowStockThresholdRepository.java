package com.tenahub.bot.repository;

import com.tenahub.bot.entity.LowStockThreshold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LowStockThresholdRepository extends JpaRepository<LowStockThreshold, Long> {

    Optional<LowStockThreshold> findByPharmacyIdAndMedicineNameIgnoreCase(Long pharmacyId, String medicineName);

    List<LowStockThreshold> findByPharmacyId(Long pharmacyId);
}