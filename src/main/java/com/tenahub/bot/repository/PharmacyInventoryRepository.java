package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PharmacyInventoryRepository extends JpaRepository<PharmacyInventory, Long> {

    List<PharmacyInventory> findByPharmacyId(Long pharmacyId);
    long countByPharmacyId(Long pharmacyId);

    Optional<PharmacyInventory> findByPharmacyIdAndMedicineNameIgnoreCase(Long pharmacyId, String medicineName);

    List<PharmacyInventory> findByMedicineNameIgnoreCaseAndQuantityGreaterThan(String medicineName, Integer quantity);
        List<PharmacyInventory> findByMedicineNameIgnoreCase(String medicineName);

    List<PharmacyInventory> findByMedicineNameContainingIgnoreCase(String medicineName);

    List<PharmacyInventory> findByCatalogMedicineId(Long catalogMedicineId);

    List<PharmacyInventory> findByCatalogMedicineIdIn(java.util.Collection<Long> catalogMedicineIds);

    @Query("""
    select distinct pi.catalogMedicineId
    from PharmacyInventory pi
    where pi.catalogMedicineId is not null
      and pi.archived = false
      and pi.outOfStock = false
      and pi.quantity is not null
      and pi.quantity > 0
      and (pi.expiryDate is null or pi.expiryDate >= CURRENT_DATE)
""")
    List<Long> findInStockCatalogMedicineIds();


    List<PharmacyInventory> findTop10ByOutOfStockFalseAndQuantityLessThanEqualOrderByQuantityAsc(Integer quantity);
    boolean existsByPharmacyIdAndMedicineNameIgnoreCase(Long pharmacyId, String medicineName);
    @Query("""
    select distinct lower(pi.medicineName)
    from PharmacyInventory pi
    where pi.medicineName is not null and trim(pi.medicineName) <> ''
""")
List<String> findAllDistinctMedicineNames();

}