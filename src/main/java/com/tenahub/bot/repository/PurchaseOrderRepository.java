package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PurchaseOrder;
import com.tenahub.bot.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);

    List<PurchaseOrder> findByPharmacyIdAndStatusOrderByCreatedAtDesc(Long pharmacyId, PurchaseOrderStatus status);

    List<PurchaseOrder> findByPharmacyIdAndSupplierIdOrderByCreatedAtDesc(Long pharmacyId, Long supplierId);

    Optional<PurchaseOrder> findByIdAndPharmacyId(Long id, Long pharmacyId);
}
