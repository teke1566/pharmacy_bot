package com.tenahub.bot.repository;

import com.tenahub.bot.entity.Pharmacy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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
Page<Pharmacy> findAllByOrderByIdDesc(Pageable pageable);
Page<Pharmacy> findByNameContainingIgnoreCaseOrderByIdDesc(String name, Pageable pageable);
Page<Pharmacy> findByPhoneContainingIgnoreCaseOrderByIdDesc(String phone, Pageable pageable);
@Query("""
SELECT p FROM Pharmacy p
WHERE p.telegramId IS NOT NULL
  AND LOWER(CONCAT('', p.telegramId)) LIKE LOWER(CONCAT('%', :telegramIdText, '%'))
ORDER BY p.id DESC
""")
Page<Pharmacy> searchByTelegramIdText(String telegramIdText, Pageable pageable);

long countByApprovedTrue();

long countByLicenseUpdateStatus(String licenseUpdateStatus);
Page<Pharmacy> findByLicenseUpdateStatusOrderByIdDesc(String licenseUpdateStatus, Pageable pageable);




    List<Pharmacy> findByApprovedTrue();



    List<Pharmacy> findTop10ByOrderByIdDesc();

    List<Pharmacy> findTop10ByApprovedTrueOrderByIdDesc();

    List<Pharmacy> findTop10ByLicenseUpdateStatusOrderByIdDesc(String status);

  List<Pharmacy> findByApprovedTrueAndLicenseExpiryDateIsNullAndLicenseSuspendedFalse();

  List<Pharmacy> findByLicenseExpiryDateBetweenAndLicenseSuspendedFalse(LocalDate startDate, LocalDate endDate);

  List<Pharmacy> findByLicenseExpiryDateBeforeAndLicenseSuspendedFalse(LocalDate date);

  List<Pharmacy> findByLicenseSuspendedTrueOrderByIdDesc();

  List<Pharmacy> findByLicenseExpiryDateBeforeOrderByIdDesc(LocalDate date);

  List<Pharmacy> findByLicenseExpiryDateBetweenOrderByIdDesc(LocalDate startDate, LocalDate endDate);

  @Query("""
SELECT p FROM Pharmacy p
WHERE p.licenseFileId IS NULL
   OR TRIM(p.licenseFileId) = ''
   OR p.licenseExpiryDate IS NULL
ORDER BY p.id DESC
""")
  List<Pharmacy> findMissingLicenseInfoOrderByIdDesc();
}