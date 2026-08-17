package com.tenahub.bot.repository;

import com.tenahub.bot.entity.PharmacyStaffPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PharmacyStaffPermissionOverrideRepository extends JpaRepository<PharmacyStaffPermissionOverride, Long> {

    List<PharmacyStaffPermissionOverride> findByStaffId(Long staffId);

    void deleteByStaffId(Long staffId);
}
