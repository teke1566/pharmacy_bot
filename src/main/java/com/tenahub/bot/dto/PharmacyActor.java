package com.tenahub.bot.dto;

import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.entity.PharmacyStaffRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyActor {
    private Long pharmacyId;
    private Long pharmacyTelegramId;
    private Long actorTelegramId;
    private Long staffId;
    private String employeeId;
    private String displayName;
    private PharmacyStaffRole role;
    @Builder.Default
    private Set<PharmacyPermission> permissions = EnumSet.noneOf(PharmacyPermission.class);

    public boolean has(PharmacyPermission permission) {
        return permission != null && permissions != null && permissions.contains(permission);
    }

    public Set<PharmacyPermission> getPermissions() {
        return permissions == null ? Collections.emptySet() : Collections.unmodifiableSet(permissions);
    }
}
