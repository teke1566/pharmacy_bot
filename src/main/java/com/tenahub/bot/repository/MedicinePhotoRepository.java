package com.tenahub.bot.repository;

import com.tenahub.bot.entity.MedicinePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicinePhotoRepository extends JpaRepository<MedicinePhoto, Long> {

    List<MedicinePhoto> findByMedicineIdOrderByMainPhotoDescSortOrderAscIdAsc(Long medicineId);

    Optional<MedicinePhoto> findByIdAndMedicineId(Long photoId, Long medicineId);

    Optional<MedicinePhoto> findFirstByMedicineIdAndMainPhotoTrueOrderBySortOrderAscIdAsc(Long medicineId);

    long countByMedicineId(Long medicineId);
}