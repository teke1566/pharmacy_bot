package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.PermissionEffect;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.entity.PharmacyStaff;
import com.tenahub.bot.entity.PharmacyStaffPermissionOverride;
import com.tenahub.bot.entity.PharmacyStaffRole;
import com.tenahub.bot.entity.PharmacyStaffStatus;
import com.tenahub.bot.repository.PharmacyStaffPermissionOverrideRepository;
import com.tenahub.bot.repository.PharmacyStaffRepository;
import com.tenahub.bot.security.RolePermissionCatalog;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyStaffAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PharmacyStaffAccessServiceImpl implements PharmacyStaffAccessService {

    private final PharmacyStaffRepository pharmacyStaffRepository;
    private final PharmacyStaffPermissionOverrideRepository overrideRepository;

    @Override
    @Transactional
    public PharmacyStaff ensureOwnerStaff(Pharmacy pharmacy) {
        if (pharmacy == null || pharmacy.getId() == null || pharmacy.getTelegramId() == null) {
            throw new MiniAppAuthException("Pharmacy not found");
        }
        return pharmacyStaffRepository.findByPharmacyIdAndTelegramId(pharmacy.getId(), pharmacy.getTelegramId())
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    PharmacyStaff owner = PharmacyStaff.builder()
                            .pharmacyId(pharmacy.getId())
                            .telegramId(pharmacy.getTelegramId())
                            .employeeId(nextEmployeeId(pharmacy.getId()))
                            .firstName(pharmacy.getName() == null ? "Owner" : pharmacy.getName())
                            .lastName("")
                            .role(PharmacyStaffRole.PHARMACY_OWNER)
                            .status(PharmacyStaffStatus.ACTIVE)
                            .joinedAt(now)
                            .lastActiveAt(now)
                            .lastLoginAt(now)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return pharmacyStaffRepository.save(owner);
                });
    }

    @Override
    @Transactional
    public PharmacyActor resolveActor(Pharmacy pharmacy, Long actorTelegramId) {
        if (pharmacy == null || pharmacy.getId() == null) {
            throw new MiniAppAuthException("Pharmacy not found");
        }
        if (actorTelegramId == null || actorTelegramId <= 0) {
            throw new MiniAppAuthException("Telegram identity required");
        }

        if (actorTelegramId.equals(pharmacy.getTelegramId())) {
            PharmacyStaff owner = ensureOwnerStaff(pharmacy);
            touchActive(owner);
            return toActor(pharmacy, owner);
        }

        PharmacyStaff staff = pharmacyStaffRepository.findByPharmacyIdAndTelegramId(pharmacy.getId(), actorTelegramId)
                .orElseThrow(() -> new MiniAppAuthException("Staff does not belong to this pharmacy"));

        if (staff.getStatus() == PharmacyStaffStatus.SUSPENDED) {
            throw new MiniAppAuthException("Staff account is suspended");
        }
        if (staff.getStatus() == PharmacyStaffStatus.DISABLED) {
            throw new MiniAppAuthException("Staff account is disabled");
        }
        if (staff.getStatus() != PharmacyStaffStatus.ACTIVE) {
            throw new MiniAppAuthException("Staff account is not active");
        }

        touchActive(staff);
        return toActor(pharmacy, staff);
    }

    @Override
    public PharmacyActor toActor(Pharmacy pharmacy, PharmacyStaff staff) {
        Set<PharmacyPermission> permissions = resolvePermissions(staff);
        return PharmacyActor.builder()
                .pharmacyId(pharmacy.getId())
                .pharmacyTelegramId(pharmacy.getTelegramId())
                .actorTelegramId(staff.getTelegramId())
                .staffId(staff.getId())
                .employeeId(staff.getEmployeeId())
                .displayName(staff.displayName())
                .role(staff.getRole())
                .permissions(permissions)
                .build();
    }

    private Set<PharmacyPermission> resolvePermissions(PharmacyStaff staff) {
        EnumSet<PharmacyPermission> effective = EnumSet.copyOf(RolePermissionCatalog.defaultsFor(staff.getRole()));
        List<PharmacyStaffPermissionOverride> overrides = overrideRepository.findByStaffId(staff.getId());
        for (PharmacyStaffPermissionOverride override : overrides) {
            if (override.getPermission() == null || override.getEffect() == null) {
                continue;
            }
            if (override.getEffect() == PermissionEffect.GRANT) {
                effective.add(override.getPermission());
            } else if (override.getEffect() == PermissionEffect.DENY) {
                effective.remove(override.getPermission());
            }
        }
        return effective;
    }

    private void touchActive(PharmacyStaff staff) {
        LocalDateTime now = LocalDateTime.now();
        staff.setLastActiveAt(now);
        staff.setUpdatedAt(now);
        pharmacyStaffRepository.save(staff);
    }

    private String nextEmployeeId(Long pharmacyId) {
        long count = pharmacyStaffRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacyId).size();
        return String.format("EMP-%05d", count + 1);
    }
}
