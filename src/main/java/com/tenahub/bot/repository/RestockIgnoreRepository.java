package com.tenahub.bot.repository;

import com.tenahub.bot.entity.RestockIgnore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RestockIgnoreRepository extends JpaRepository<RestockIgnore, Long> {

    Optional<RestockIgnore> findByPharmacyIdAndMedicineNameIgnoreCase(Long pharmacyId, String medicineName);

    List<RestockIgnore> findByPharmacyIdAndIgnoredAtAfter(Long pharmacyId, LocalDateTime after);
}
