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
import com.tenahub.bot.service.MiniAppAuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyStaffAccessServiceImplTest {

    @Mock
    private PharmacyStaffRepository pharmacyStaffRepository;
    @Mock
    private PharmacyStaffPermissionOverrideRepository overrideRepository;

    @InjectMocks
    private PharmacyStaffAccessServiceImpl service;

    @Test
    void ensureOwnerStaff_createsOwnerWhenMissing() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).name("Tena").build();
        when(pharmacyStaffRepository.findByPharmacyIdAndTelegramId(3L, 9001L)).thenReturn(Optional.empty());
        when(pharmacyStaffRepository.findByPharmacyIdOrderByCreatedAtDesc(3L)).thenReturn(List.of());
        when(pharmacyStaffRepository.save(any())).thenAnswer(inv -> {
            PharmacyStaff s = inv.getArgument(0);
            s.setId(11L);
            return s;
        });

        PharmacyStaff owner = service.ensureOwnerStaff(pharmacy);

        assertEquals(PharmacyStaffRole.PHARMACY_OWNER, owner.getRole());
        assertEquals(PharmacyStaffStatus.ACTIVE, owner.getStatus());
        assertEquals(9001L, owner.getTelegramId());
        assertEquals("EMP-00001", owner.getEmployeeId());
    }

    @Test
    void resolveActor_allowsActiveStaffAndAppliesDenyOverride() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).build();
        PharmacyStaff staff = PharmacyStaff.builder()
                .id(22L)
                .pharmacyId(3L)
                .telegramId(777L)
                .employeeId("EMP-00002")
                .firstName("Abebe")
                .lastName("Kebede")
                .role(PharmacyStaffRole.PHARMACIST)
                .status(PharmacyStaffStatus.ACTIVE)
                .build();
        when(pharmacyStaffRepository.findByPharmacyIdAndTelegramId(3L, 777L)).thenReturn(Optional.of(staff));
        when(overrideRepository.findByStaffId(22L)).thenReturn(List.of(
                PharmacyStaffPermissionOverride.builder()
                        .permission(PharmacyPermission.PRICE_VIEW)
                        .effect(PermissionEffect.DENY)
                        .build()
        ));
        when(pharmacyStaffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PharmacyActor actor = service.resolveActor(pharmacy, 777L);

        assertEquals(9001L, actor.getPharmacyTelegramId());
        assertEquals(777L, actor.getActorTelegramId());
        assertTrue(actor.has(PharmacyPermission.PRESCRIPTION_APPROVE));
        assertFalse(actor.has(PharmacyPermission.PRICE_VIEW));
        assertFalse(actor.has(PharmacyPermission.STAFF_CREATE));
    }

    @Test
    void resolveActor_blocksSuspendedStaff() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).build();
        when(pharmacyStaffRepository.findByPharmacyIdAndTelegramId(3L, 777L)).thenReturn(Optional.of(
                PharmacyStaff.builder()
                        .id(22L)
                        .telegramId(777L)
                        .role(PharmacyStaffRole.PHARMACIST)
                        .status(PharmacyStaffStatus.SUSPENDED)
                        .build()
        ));

        MiniAppAuthException error = assertThrows(MiniAppAuthException.class,
                () -> service.resolveActor(pharmacy, 777L));
        assertEquals("Staff account is suspended", error.getMessage());
    }

    @Test
    void resolveActor_blocksOtherPharmacyStaff() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).build();
        when(pharmacyStaffRepository.findByPharmacyIdAndTelegramId(3L, 555L)).thenReturn(Optional.empty());

        assertThrows(MiniAppAuthException.class, () -> service.resolveActor(pharmacy, 555L));
    }

    @Test
    void resolveActor_ownerBootstrapsAndGetsAllPermissions() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).name("Tena").build();
        when(pharmacyStaffRepository.findByPharmacyIdAndTelegramId(3L, 9001L)).thenReturn(Optional.empty());
        when(pharmacyStaffRepository.findByPharmacyIdOrderByCreatedAtDesc(3L)).thenReturn(List.of());
        when(pharmacyStaffRepository.save(any())).thenAnswer(inv -> {
            PharmacyStaff s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(1L);
            }
            return s;
        });
        when(overrideRepository.findByStaffId(1L)).thenReturn(List.of());

        PharmacyActor actor = service.resolveActor(pharmacy, 9001L);

        assertEquals(PharmacyStaffRole.PHARMACY_OWNER, actor.getRole());
        assertTrue(actor.has(PharmacyPermission.STAFF_PERMISSION_MANAGE));
        ArgumentCaptor<PharmacyStaff> captor = ArgumentCaptor.forClass(PharmacyStaff.class);
        verify(pharmacyStaffRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    }
}
