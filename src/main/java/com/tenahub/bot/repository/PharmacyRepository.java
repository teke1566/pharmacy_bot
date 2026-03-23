package com.tenahub.bot.repository;

import com.tenahub.bot.entity.Pharmacy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

    List<Pharmacy> findByMedicinesContainingIgnoreCase(String medicine);
      Optional<Pharmacy> findByTelegramId(Long telegramId);

    List<Pharmacy> findByMedicinesContainingIgnoreCaseAndAreaContainingIgnoreCase(String medicine, String area);

    List<Pharmacy> findByMedicinesContainingIgnoreCaseAndCityContainingIgnoreCase(String medicine, String city);
    @Query("SELECT DISTINCT p.medicines FROM Pharmacy p")
    List<String> findAllMedicines();
    @Query("""
SELECT p FROM Pharmacy p
WHERE LOWER(p.medicines) LIKE LOWER(CONCAT('%',:medicine,'%'))
""")
List<Pharmacy> searchMedicine(String medicine);
boolean existsByTelegramId(Long telegramId);
List<Pharmacy> findByLicenseUpdateStatusOrderByIdDesc(String licenseUpdateStatus);

long countByApprovedTrue();

long countByLicenseUpdateStatus(String licenseUpdateStatus);
Page<Pharmacy> findByLicenseUpdateStatusOrderByIdDesc(String licenseUpdateStatus, Pageable pageable);




    List<Pharmacy> findByApprovedTrue();



    List<Pharmacy> findTop10ByOrderByIdDesc();

    List<Pharmacy> findTop10ByApprovedTrueOrderByIdDesc();

    List<Pharmacy> findTop10ByLicenseUpdateStatusOrderByIdDesc(String status);
}