package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PharmacyRegistrationRepository extends JpaRepository<PharmacyRegistration, Long> {

    List<PharmacyRegistration> findAllByTelegramId(Long telegramId);

    boolean existsByTelegramIdAndStatus(Long telegramId, String status);

    Optional<PharmacyRegistration> findTopByTelegramIdAndStatusOrderByIdDesc(Long telegramId, String status);

    Optional<PharmacyRegistration> findFirstByTelegramIdAndStatusOrderByIdDesc(Long telegramId, String status);

    List<PharmacyRegistration> findByStatusOrderByIdDesc(String status);

    long countByStatus(String status);

    Page<PharmacyRegistration> findByStatusOrderByIdDesc(String status, Pageable pageable);

    List<PharmacyRegistration> findTop10ByStatusOrderByIdDesc(String status);

    void deleteByTelegramId(Long telegramId);

    @Query("""
    select r from PharmacyRegistration r
    where r.status = 'PENDING'
      and (
           r.name is null or trim(r.name) = '' or
           r.city is null or trim(r.city) = '' or
           r.area is null or trim(r.area) = '' or
           r.phone is null or trim(r.phone) = '' or
           r.medicines is null or trim(r.medicines) = '' or
           r.openTime is null or trim(r.openTime) = '' or
           r.closeTime is null or trim(r.closeTime) = '' or
           r.telegramId is null or
           r.latitude is null or
           r.longitude is null or
           r.licenseFileId is null or trim(r.licenseFileId) = ''
      )
""")
List<PharmacyRegistration> findInvalidPendingRegistrations();
Optional<PharmacyRegistration> findTopByTelegramIdOrderByIdDesc(Long telegramId);
   



}
