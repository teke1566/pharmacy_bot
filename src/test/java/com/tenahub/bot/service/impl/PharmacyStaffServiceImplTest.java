package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.dto.PharmacyStaffDTO;
import com.tenahub.bot.dto.PharmacyStaffInviteRequestDTO;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.entity.PharmacyStaff;
import com.tenahub.bot.entity.PharmacyStaffInvite;
import com.tenahub.bot.entity.PharmacyStaffRole;
import com.tenahub.bot.entity.PharmacyStaffStatus;
import com.tenahub.bot.repository.PharmacyAuditEventRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacyStaffInviteRepository;
import com.tenahub.bot.repository.PharmacyStaffPermissionOverrideRepository;
import com.tenahub.bot.repository.PharmacyStaffRepository;
import com.tenahub.bot.service.PharmacyAuditService;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacyStaffAccessService;
import com.tenahub.bot.service.MiniAppAuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyStaffServiceImplTest {

    @Mock private PharmacyRepository pharmacyRepository;
    @Mock private PharmacyStaffRepository pharmacyStaffRepository;
    @Mock private PharmacyStaffInviteRepository inviteRepository;
    @Mock private PharmacyStaffPermissionOverrideRepository overrideRepository;
    @Mock private PharmacyAuditEventRepository auditEventRepository;
    @Mock private PharmacyStaffAccessService staffAccessService;
    @Mock private PharmacyAuthorizationService authorizationService;
    @Mock private PharmacyAuditService pharmacyAuditService;

    @InjectMocks
    private PharmacyStaffServiceImpl service;

    private PharmacyActor admin() {
        return PharmacyActor.builder()
                .pharmacyId(3L)
                .pharmacyTelegramId(9001L)
                .actorTelegramId(9001L)
                .staffId(1L)
                .employeeId("EMP-00001")
                .displayName("Owner")
                .role(PharmacyStaffRole.PHARMACY_OWNER)
                .permissions(EnumSet.allOf(PharmacyPermission.class))
                .build();
    }

    @Test
    void invite_createsInvitedStaffAndToken() {
        when(pharmacyStaffRepository.findByPharmacyIdOrderByCreatedAtDesc(3L)).thenReturn(List.of());
        when(pharmacyStaffRepository.save(any())).thenAnswer(inv -> {
            PharmacyStaff s = inv.getArgument(0);
            s.setId(22L);
            return s;
        });
        when(inviteRepository.findFirstByStaffIdAndRevokedAtIsNullAndAcceptedAtIsNullOrderByCreatedAtDesc(22L))
                .thenReturn(Optional.empty());
        when(inviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(overrideRepository.findByStaffId(22L)).thenReturn(List.of());

        PharmacyStaffDTO dto = service.invite(admin(), PharmacyStaffInviteRequestDTO.builder()
                .firstName("Abebe")
                .lastName("Kebede")
                .role("PHARMACIST")
                .invitedTelegramId(777L)
                .build());

        assertEquals(PharmacyStaffStatus.INVITED.name(), dto.getStatus());
        assertEquals("Abebe Kebede", dto.getDisplayName());
        assertNotNull(dto.getInviteToken());
        verify(pharmacyAuditService).record(any(), org.mockito.ArgumentMatchers.eq("STAFF_INVITED"),
                org.mockito.ArgumentMatchers.eq("STAFF"), org.mockito.ArgumentMatchers.eq("PharmacyStaff"),
                any(), any(), any(), any());
    }

    @Test
    void suspend_blockedWithoutPermission() {
        doThrow(new MiniAppAuthException("Missing permission: STAFF_DISABLE"))
                .when(authorizationService).require(any(), org.mockito.ArgumentMatchers.eq(PharmacyPermission.STAFF_DISABLE));

        assertThrows(MiniAppAuthException.class, () -> service.suspend(admin(), 9L, "leave"));
    }

    @Test
    void acceptInvite_activatesStaff() {
        PharmacyStaff staff = PharmacyStaff.builder()
                .id(22L)
                .pharmacyId(3L)
                .employeeId("EMP-00002")
                .role(PharmacyStaffRole.PHARMACIST)
                .status(PharmacyStaffStatus.INVITED)
                .build();
        PharmacyStaffInvite invite = PharmacyStaffInvite.builder()
                .id(1L)
                .staffId(22L)
                .pharmacyId(3L)
                .tokenHash(sha("abc"))
                .expiresAt(java.time.LocalDateTime.now().plusDays(1))
                .build();
        when(inviteRepository.findByTokenHashAndRevokedAtIsNullAndAcceptedAtIsNull(sha("abc")))
                .thenReturn(Optional.of(invite));
        when(pharmacyStaffRepository.findById(22L)).thenReturn(Optional.of(staff));
        when(pharmacyStaffRepository.existsByPharmacyIdAndTelegramId(3L, 777L)).thenReturn(false);
        when(pharmacyStaffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pharmacyRepository.findById(3L)).thenReturn(Optional.of(
                com.tenahub.bot.entity.Pharmacy.builder().id(3L).telegramId(9001L).build()));
        when(overrideRepository.findByStaffId(22L)).thenReturn(List.of());

        PharmacyStaffDTO dto = service.acceptInvite("abc", 777L);

        assertEquals(PharmacyStaffStatus.ACTIVE.name(), dto.getStatus());
        assertEquals(777L, dto.getTelegramId());
    }

    private String sha(String raw) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
