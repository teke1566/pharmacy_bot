package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacyPhotoRepository extends JpaRepository<PharmacyPhoto, Long> {

    List<PharmacyPhoto> findByPharmacyIdOrderByMainPhotoDescSortOrderAscIdAsc(Long pharmacyId);

    long countByPharmacyId(Long pharmacyId);

    Optional<PharmacyPhoto> findFirstByPharmacyIdAndMainPhotoTrueOrderBySortOrderAscIdAsc(Long pharmacyId);
}
