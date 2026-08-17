package com.tenahub.bot.repository;

import com.tenahub.bot.entity.MedicineBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {

    List<MedicineBatch> findByInventoryId(Long inventoryId);

    List<MedicineBatch> findByPharmacyId(Long pharmacyId);

    List<MedicineBatch> findByPharmacyIdAndInventoryId(Long pharmacyId, Long inventoryId);

    long countByInventoryId(Long inventoryId);
}
