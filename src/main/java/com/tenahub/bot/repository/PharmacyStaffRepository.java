package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyStaff;
import com.tenahub.bot.entity.PharmacyStaffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacyStaffRepository extends JpaRepository<PharmacyStaff, Long> {

    List<PharmacyStaff> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);

    Optional<PharmacyStaff> findByIdAndPharmacyId(Long id, Long pharmacyId);

    Optional<PharmacyStaff> findByPharmacyIdAndTelegramId(Long pharmacyId, Long telegramId);

    Optional<PharmacyStaff> findByPharmacyIdAndEmployeeIdIgnoreCase(Long pharmacyId, String employeeId);

    long countByPharmacyIdAndStatus(Long pharmacyId, PharmacyStaffStatus status);

    boolean existsByPharmacyIdAndTelegramId(Long pharmacyId, Long telegramId);
}
