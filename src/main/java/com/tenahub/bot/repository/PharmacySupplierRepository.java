package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacySupplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacySupplierRepository extends JpaRepository<PharmacySupplier, Long> {

    List<PharmacySupplier> findByPharmacyIdOrderByNameAsc(Long pharmacyId);

    List<PharmacySupplier> findByPharmacyIdAndNameContainingIgnoreCaseOrderByNameAsc(Long pharmacyId, String name);

    Optional<PharmacySupplier> findByIdAndPharmacyId(Long id, Long pharmacyId);
}
