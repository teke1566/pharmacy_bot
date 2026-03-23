package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PharmacyInventoryRepository extends JpaRepository<PharmacyInventory, Long> {

    List<PharmacyInventory> findByPharmacyId(Long pharmacyId);

    Optional<PharmacyInventory> findByPharmacyIdAndMedicineNameIgnoreCase(Long pharmacyId, String medicineName);

    List<PharmacyInventory> findByMedicineNameIgnoreCaseAndQuantityGreaterThan(String medicineName, Integer quantity);
        List<PharmacyInventory> findByMedicineNameIgnoreCase(String medicineName);


    List<PharmacyInventory> findTop10ByOutOfStockFalseAndQuantityLessThanEqualOrderByQuantityAsc(Integer quantity);
    boolean existsByPharmacyIdAndMedicineNameIgnoreCase(Long pharmacyId, String medicineName);
    @Query("""
    select distinct lower(pi.medicineName)
    from PharmacyInventory pi
    where pi.medicineName is not null and trim(pi.medicineName) <> ''
""")
List<String> findAllDistinctMedicineNames();

}