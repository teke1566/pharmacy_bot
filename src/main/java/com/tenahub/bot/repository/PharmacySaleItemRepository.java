package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacySaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PharmacySaleItemRepository extends JpaRepository<PharmacySaleItem, Long> {

    List<PharmacySaleItem> findBySaleIdOrderByIdAsc(Long saleId);

    List<PharmacySaleItem> findByPharmacyIdAndSaleIdIn(Long pharmacyId, List<Long> saleIds);
}
