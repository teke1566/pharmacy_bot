package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyStaffInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PharmacyStaffInviteRepository extends JpaRepository<PharmacyStaffInvite, Long> {

    Optional<PharmacyStaffInvite> findByTokenHashAndRevokedAtIsNullAndAcceptedAtIsNull(String tokenHash);

    Optional<PharmacyStaffInvite> findFirstByStaffIdAndRevokedAtIsNullAndAcceptedAtIsNullOrderByCreatedAtDesc(Long staffId);
}
