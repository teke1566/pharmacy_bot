package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.dto.PharmacyAuditEventDTO;
import com.tenahub.bot.dto.PharmacyStaffDTO;
import com.tenahub.bot.dto.PharmacyStaffInviteRequestDTO;
import com.tenahub.bot.dto.PharmacyStaffMetricsDTO;
import com.tenahub.bot.dto.PharmacyStaffUpdateRequestDTO;
import com.tenahub.bot.entity.PermissionEffect;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyAuditEvent;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.entity.PharmacyStaff;
import com.tenahub.bot.entity.PharmacyStaffInvite;
import com.tenahub.bot.entity.PharmacyStaffPermissionOverride;
import com.tenahub.bot.entity.PharmacyStaffRole;
import com.tenahub.bot.entity.PharmacyStaffStatus;
import com.tenahub.bot.repository.PharmacyAuditEventRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacyStaffInviteRepository;
import com.tenahub.bot.repository.PharmacyStaffPermissionOverrideRepository;
import com.tenahub.bot.repository.PharmacyStaffRepository;
import com.tenahub.bot.security.RolePermissionCatalog;
import com.tenahub.bot.service.PharmacyAuditService;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacyStaffAccessService;
import com.tenahub.bot.service.PharmacyStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PharmacyStaffServiceImpl implements PharmacyStaffService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyStaffRepository pharmacyStaffRepository;
    private final PharmacyStaffInviteRepository inviteRepository;
    private final PharmacyStaffPermissionOverrideRepository overrideRepository;
    private final PharmacyAuditEventRepository auditEventRepository;
    private final PharmacyStaffAccessService staffAccessService;
    private final PharmacyAuthorizationService authorizationService;
    private final PharmacyAuditService pharmacyAuditService;

    @Override
    @Transactional(readOnly = true)
    public List<PharmacyStaffDTO> list(PharmacyActor actor) {
        authorizationService.require(actor, PharmacyPermission.STAFF_VIEW);
        staffAccessService.ensureOwnerStaff(requirePharmacy(actor));
        return pharmacyStaffRepository.findByPharmacyIdOrderByCreatedAtDesc(actor.getPharmacyId()).stream()
                .map(s -> toDto(s, false, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PharmacyStaffDTO get(PharmacyActor actor, Long staffId) {
        authorizationService.require(actor, PharmacyPermission.STAFF_VIEW);
        return toDto(requireStaff(actor.getPharmacyId(), staffId), true, null);
    }

    @Override
    @Transactional
    public PharmacyStaffDTO invite(PharmacyActor actor, PharmacyStaffInviteRequestDTO request) {
        authorizationService.require(actor, PharmacyPermission.STAFF_CREATE);
        if (request == null) {
            throw new RuntimeException("Invite payload is required");
        }
        PharmacyStaffRole role = parseRole(request.getRole());
        if (role == PharmacyStaffRole.PHARMACY_OWNER) {
            throw new RuntimeException("Cannot invite another owner");
        }
        LocalDateTime now = LocalDateTime.now();
        String employeeId = normalizeEmployeeId(request.getEmployeeId());
        if (employeeId == null || employeeId.isBlank()) {
            employeeId = nextEmployeeId(actor.getPharmacyId());
        } else if (pharmacyStaffRepository.findByPharmacyIdAndEmployeeIdIgnoreCase(actor.getPharmacyId(), employeeId).isPresent()) {
            throw new RuntimeException("Employee ID already exists in this pharmacy");
        }
        if (request.getInvitedTelegramId() != null
                && pharmacyStaffRepository.existsByPharmacyIdAndTelegramId(actor.getPharmacyId(), request.getInvitedTelegramId())) {
            throw new RuntimeException("Telegram user is already a staff member");
        }

        PharmacyStaff staff = PharmacyStaff.builder()
                .pharmacyId(actor.getPharmacyId())
                .telegramId(null)
                .employeeId(employeeId)
                .firstName(trim(request.getFirstName()))
                .lastName(trim(request.getLastName()))
                .email(trim(request.getEmail()))
                .phone(trim(request.getPhone()))
                .role(role)
                .status(PharmacyStaffStatus.INVITED)
                .licenseInfo(trim(request.getLicenseInfo()))
                .notes(trim(request.getNotes()))
                .startDate(request.getStartDate())
                .invitedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        staff = pharmacyStaffRepository.save(staff);
        replaceOverrides(actor, staff, request.getGrantPermissions(), request.getDenyPermissions());

        String rawToken = createInviteToken(actor, staff, request.getInvitedTelegramId());
        pharmacyAuditService.record(actor, "STAFF_INVITED", "STAFF", "PharmacyStaff",
                String.valueOf(staff.getId()), null, staff.getEmployeeId() + ":" + role, null);

        return toDto(staff, true, rawToken);
    }

    @Override
    @Transactional
    public PharmacyStaffDTO update(PharmacyActor actor, Long staffId, PharmacyStaffUpdateRequestDTO request) {
        authorizationService.require(actor, PharmacyPermission.STAFF_EDIT);
        PharmacyStaff staff = requireStaff(actor.getPharmacyId(), staffId);
        if (staff.getRole() == PharmacyStaffRole.PHARMACY_OWNER && actor.getStaffId() != null
                && !actor.getStaffId().equals(staff.getId())) {
            throw new RuntimeException("Cannot edit pharmacy owner profile");
        }
        if (request.getFirstName() != null) staff.setFirstName(trim(request.getFirstName()));
        if (request.getLastName() != null) staff.setLastName(trim(request.getLastName()));
        if (request.getEmail() != null) staff.setEmail(trim(request.getEmail()));
        if (request.getPhone() != null) staff.setPhone(trim(request.getPhone()));
        if (request.getLicenseInfo() != null) staff.setLicenseInfo(trim(request.getLicenseInfo()));
        if (request.getNotes() != null) staff.setNotes(trim(request.getNotes()));
        if (request.getPhotoUrl() != null) staff.setPhotoUrl(trim(request.getPhotoUrl()));
        if (request.getStartDate() != null) staff.setStartDate(request.getStartDate());
        if (request.getRole() != null && !request.getRole().isBlank()) {
            PharmacyStaffRole role = parseRole(request.getRole());
            if (staff.getRole() == PharmacyStaffRole.PHARMACY_OWNER && role != PharmacyStaffRole.PHARMACY_OWNER) {
                throw new RuntimeException("Cannot demote pharmacy owner");
            }
            if (role == PharmacyStaffRole.PHARMACY_OWNER && staff.getRole() != PharmacyStaffRole.PHARMACY_OWNER) {
                throw new RuntimeException("Cannot promote to owner via update");
            }
            staff.setRole(role);
        }
        staff.setUpdatedAt(LocalDateTime.now());
        pharmacyStaffRepository.save(staff);
        if (request.getGrantPermissions() != null || request.getDenyPermissions() != null) {
            authorizationService.require(actor, PharmacyPermission.STAFF_PERMISSION_MANAGE);
            replaceOverrides(actor, staff, request.getGrantPermissions(), request.getDenyPermissions());
        }
        pharmacyAuditService.record(actor, "STAFF_UPDATED", "STAFF", "PharmacyStaff",
                String.valueOf(staff.getId()), null, staff.getRole().name(), null);
        return toDto(staff, true, null);
    }

    @Override
    @Transactional
    public PharmacyStaffDTO suspend(PharmacyActor actor, Long staffId, String reason) {
        authorizationService.require(actor, PharmacyPermission.STAFF_DISABLE);
        PharmacyStaff staff = requireStaff(actor.getPharmacyId(), staffId);
        assertNotSelfOrOwner(actor, staff);
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("Suspension reason is required");
        }
        staff.setStatus(PharmacyStaffStatus.SUSPENDED);
        staff.setSuspendedAt(LocalDateTime.now());
        staff.setSuspendReason(reason.trim());
        staff.setUpdatedAt(LocalDateTime.now());
        pharmacyStaffRepository.save(staff);
        pharmacyAuditService.record(actor, "STAFF_SUSPENDED", "STAFF", "PharmacyStaff",
                String.valueOf(staff.getId()), PharmacyStaffStatus.ACTIVE.name(), PharmacyStaffStatus.SUSPENDED.name(), reason);
        return toDto(staff, true, null);
    }

    @Override
    @Transactional
    public PharmacyStaffDTO activate(PharmacyActor actor, Long staffId) {
        authorizationService.require(actor, PharmacyPermission.STAFF_EDIT);
        PharmacyStaff staff = requireStaff(actor.getPharmacyId(), staffId);
        if (staff.getTelegramId() == null) {
            throw new RuntimeException("Staff must accept invitation before activation");
        }
        staff.setStatus(PharmacyStaffStatus.ACTIVE);
        staff.setSuspendedAt(null);
        staff.setSuspendReason(null);
        staff.setDisabledAt(null);
        staff.setUpdatedAt(LocalDateTime.now());
        pharmacyStaffRepository.save(staff);
        pharmacyAuditService.record(actor, "STAFF_ACTIVATED", "STAFF", "PharmacyStaff",
                String.valueOf(staff.getId()), null, PharmacyStaffStatus.ACTIVE.name(), null);
        return toDto(staff, true, null);
    }

    @Override
    @Transactional
    public PharmacyStaffDTO disable(PharmacyActor actor, Long staffId, String reason) {
        authorizationService.require(actor, PharmacyPermission.STAFF_DISABLE);
        PharmacyStaff staff = requireStaff(actor.getPharmacyId(), staffId);
        assertNotSelfOrOwner(actor, staff);
        staff.setStatus(PharmacyStaffStatus.DISABLED);
        staff.setDisabledAt(LocalDateTime.now());
        staff.setUpdatedAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            staff.setSuspendReason(reason.trim());
        }
        pharmacyStaffRepository.save(staff);
        pharmacyAuditService.record(actor, "STAFF_DISABLED", "STAFF", "PharmacyStaff",
                String.valueOf(staff.getId()), null, PharmacyStaffStatus.DISABLED.name(), reason);
        return toDto(staff, true, null);
    }

    @Override
    @Transactional
    public PharmacyStaffDTO acceptInvite(String rawToken, Long actorTelegramId) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RuntimeException("Invite token is required");
        }
        if (actorTelegramId == null || actorTelegramId <= 0) {
            throw new RuntimeException("Telegram identity required");
        }
        String hash = sha256(rawToken.trim());
        PharmacyStaffInvite invite = inviteRepository.findByTokenHashAndRevokedAtIsNullAndAcceptedAtIsNull(hash)
                .orElseThrow(() -> new RuntimeException("Invite not found or already used"));
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invite has expired");
        }
        if (invite.getInvitedTelegramId() != null && !invite.getInvitedTelegramId().equals(actorTelegramId)) {
            throw new RuntimeException("Invite was issued for a different Telegram user");
        }
        PharmacyStaff staff = pharmacyStaffRepository.findById(invite.getStaffId())
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        if (pharmacyStaffRepository.existsByPharmacyIdAndTelegramId(staff.getPharmacyId(), actorTelegramId)
                && (staff.getTelegramId() == null || !staff.getTelegramId().equals(actorTelegramId))) {
            throw new RuntimeException("Telegram user is already linked to another staff record");
        }
        LocalDateTime now = LocalDateTime.now();
        staff.setTelegramId(actorTelegramId);
        staff.setStatus(PharmacyStaffStatus.ACTIVE);
        staff.setJoinedAt(now);
        staff.setLastLoginAt(now);
        staff.setLastActiveAt(now);
        staff.setUpdatedAt(now);
        pharmacyStaffRepository.save(staff);
        invite.setAcceptedAt(now);
        inviteRepository.save(invite);

        Pharmacy pharmacy = pharmacyRepository.findById(staff.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
        PharmacyActor systemActor = PharmacyActor.builder()
                .pharmacyId(pharmacy.getId())
                .pharmacyTelegramId(pharmacy.getTelegramId())
                .actorTelegramId(actorTelegramId)
                .staffId(staff.getId())
                .employeeId(staff.getEmployeeId())
                .displayName(staff.displayName())
                .role(staff.getRole())
                .permissions(EnumSet.noneOf(PharmacyPermission.class))
                .build();
        pharmacyAuditService.record(systemActor, "STAFF_INVITE_ACCEPTED", "STAFF", "PharmacyStaff",
                String.valueOf(staff.getId()), PharmacyStaffStatus.INVITED.name(), PharmacyStaffStatus.ACTIVE.name(), null);
        return toDto(staff, true, null);
    }

    @Override
    @Transactional
    public PharmacyStaffDTO replacePermissions(PharmacyActor actor, Long staffId,
                                               List<String> grants, List<String> denials) {
        authorizationService.require(actor, PharmacyPermission.STAFF_PERMISSION_MANAGE);
        PharmacyStaff staff = requireStaff(actor.getPharmacyId(), staffId);
        if (staff.getRole() == PharmacyStaffRole.PHARMACY_OWNER) {
            throw new RuntimeException("Owner permissions cannot be overridden");
        }
        replaceOverrides(actor, staff, grants, denials);
        pharmacyAuditService.record(actor, "STAFF_PERMISSIONS_UPDATED", "STAFF", "PharmacyStaff",
                String.valueOf(staff.getId()), null, "grants=" + grants + ";denies=" + denials, null);
        return toDto(staff, true, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PharmacyAuditEventDTO> activity(PharmacyActor actor, Long staffId,
                                                LocalDateTime from, LocalDateTime to) {
        authorizationService.require(actor, PharmacyPermission.AUDIT_VIEW);
        requireStaff(actor.getPharmacyId(), staffId);
        return pharmacyAuditService.listForStaff(actor.getPharmacyId(), staffId, from, to).stream()
                .map(this::toAuditDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PharmacyStaffMetricsDTO metrics(PharmacyActor actor) {
        authorizationService.require(actor, PharmacyPermission.STAFF_VIEW);
        Long pharmacyId = actor.getPharmacyId();
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long activeToday = pharmacyStaffRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacyId).stream()
                .filter(s -> s.getStatus() == PharmacyStaffStatus.ACTIVE)
                .filter(s -> s.getLastActiveAt() != null && !s.getLastActiveAt().isBefore(todayStart))
                .count();
        return PharmacyStaffMetricsDTO.builder()
                .activeStaff(pharmacyStaffRepository.countByPharmacyIdAndStatus(pharmacyId, PharmacyStaffStatus.ACTIVE))
                .invitedStaff(pharmacyStaffRepository.countByPharmacyIdAndStatus(pharmacyId, PharmacyStaffStatus.INVITED))
                .suspendedStaff(pharmacyStaffRepository.countByPharmacyIdAndStatus(pharmacyId, PharmacyStaffStatus.SUSPENDED))
                .disabledStaff(pharmacyStaffRepository.countByPharmacyIdAndStatus(pharmacyId, PharmacyStaffStatus.DISABLED))
                .activeToday(activeToday)
                .auditEventsToday(auditEventRepository.countByPharmacyIdAndCreatedAtGreaterThanEqual(pharmacyId, todayStart))
                .build();
    }

    @Override
    public Map<String, Object> rolesCatalog() {
        Map<String, Object> roles = new LinkedHashMap<>();
        for (PharmacyStaffRole role : PharmacyStaffRole.values()) {
            roles.put(role.name(), RolePermissionCatalog.defaultsFor(role).stream()
                    .map(Enum::name)
                    .sorted()
                    .toList());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roles", roles);
        body.put("permissions", List.of(PharmacyPermission.values()).stream().map(Enum::name).toList());
        return body;
    }

    private void replaceOverrides(PharmacyActor actor, PharmacyStaff staff,
                                  List<String> grants, List<String> denials) {
        overrideRepository.deleteByStaffId(staff.getId());
        LocalDateTime now = LocalDateTime.now();
        List<PharmacyStaffPermissionOverride> rows = new ArrayList<>();
        for (PharmacyPermission permission : parsePermissions(grants)) {
            rows.add(PharmacyStaffPermissionOverride.builder()
                    .pharmacyId(staff.getPharmacyId())
                    .staffId(staff.getId())
                    .permission(permission)
                    .effect(PermissionEffect.GRANT)
                    .createdAt(now)
                    .createdByTelegramId(actor.getActorTelegramId())
                    .build());
        }
        for (PharmacyPermission permission : parsePermissions(denials)) {
            rows.add(PharmacyStaffPermissionOverride.builder()
                    .pharmacyId(staff.getPharmacyId())
                    .staffId(staff.getId())
                    .permission(permission)
                    .effect(PermissionEffect.DENY)
                    .createdAt(now)
                    .createdByTelegramId(actor.getActorTelegramId())
                    .build());
        }
        if (!rows.isEmpty()) {
            overrideRepository.saveAll(rows);
        }
    }

    private String createInviteToken(PharmacyActor actor, PharmacyStaff staff, Long invitedTelegramId) {
        inviteRepository.findFirstByStaffIdAndRevokedAtIsNullAndAcceptedAtIsNullOrderByCreatedAtDesc(staff.getId())
                .ifPresent(existing -> {
                    existing.setRevokedAt(LocalDateTime.now());
                    inviteRepository.save(existing);
                });
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String raw = HexFormat.of().formatHex(bytes);
        PharmacyStaffInvite invite = PharmacyStaffInvite.builder()
                .pharmacyId(staff.getPharmacyId())
                .staffId(staff.getId())
                .tokenHash(sha256(raw))
                .invitedTelegramId(invitedTelegramId)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .createdByTelegramId(actor.getActorTelegramId())
                .build();
        inviteRepository.save(invite);
        return raw;
    }

    private PharmacyStaffDTO toDto(PharmacyStaff staff, boolean includePermissions, String inviteToken) {
        List<PharmacyStaffPermissionOverride> overrides = includePermissions
                ? overrideRepository.findByStaffId(staff.getId())
                : List.of();
        Set<PharmacyPermission> effective = EnumSet.copyOf(RolePermissionCatalog.defaultsFor(staff.getRole()));
        List<String> grants = new ArrayList<>();
        List<String> denials = new ArrayList<>();
        for (PharmacyStaffPermissionOverride override : overrides) {
            if (override.getEffect() == PermissionEffect.GRANT) {
                effective.add(override.getPermission());
                grants.add(override.getPermission().name());
            } else if (override.getEffect() == PermissionEffect.DENY) {
                effective.remove(override.getPermission());
                denials.add(override.getPermission().name());
            }
        }
        return PharmacyStaffDTO.builder()
                .staffId(staff.getId())
                .employeeId(staff.getEmployeeId())
                .telegramId(staff.getTelegramId())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .displayName(staff.displayName())
                .email(staff.getEmail())
                .phone(staff.getPhone())
                .photoUrl(staff.getPhotoUrl())
                .role(staff.getRole() == null ? null : staff.getRole().name())
                .status(staff.getStatus() == null ? null : staff.getStatus().name())
                .startDate(staff.getStartDate())
                .invitedAt(staff.getInvitedAt())
                .joinedAt(staff.getJoinedAt())
                .lastActiveAt(staff.getLastActiveAt())
                .lastLoginAt(staff.getLastLoginAt())
                .licenseInfo(staff.getLicenseInfo())
                .notes(staff.getNotes())
                .suspendReason(staff.getSuspendReason())
                .permissions(includePermissions ? effective.stream().map(Enum::name).sorted().toList() : null)
                .grantedOverrides(includePermissions ? grants : null)
                .deniedOverrides(includePermissions ? denials : null)
                .inviteToken(inviteToken)
                .build();
    }

    private PharmacyAuditEventDTO toAuditDto(PharmacyAuditEvent event) {
        return PharmacyAuditEventDTO.builder()
                .eventId(event.getId())
                .staffId(event.getStaffId())
                .employeeId(event.getEmployeeId())
                .userName(event.getUserNameSnapshot())
                .action(event.getAction())
                .module(event.getModule())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .oldValue(event.getOldValue())
                .newValue(event.getNewValue())
                .reason(event.getReason())
                .correlationId(event.getCorrelationId())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private Pharmacy requirePharmacy(PharmacyActor actor) {
        return pharmacyRepository.findById(actor.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }

    private PharmacyStaff requireStaff(Long pharmacyId, Long staffId) {
        return pharmacyStaffRepository.findByIdAndPharmacyId(staffId, pharmacyId)
                .orElseThrow(() -> new RuntimeException("Staff does not belong to this pharmacy"));
    }

    private void assertNotSelfOrOwner(PharmacyActor actor, PharmacyStaff staff) {
        if (staff.getRole() == PharmacyStaffRole.PHARMACY_OWNER) {
            throw new RuntimeException("Cannot suspend or disable pharmacy owner");
        }
        if (actor.getStaffId() != null && actor.getStaffId().equals(staff.getId())) {
            throw new RuntimeException("Cannot suspend or disable yourself");
        }
    }

    private PharmacyStaffRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new RuntimeException("Role is required");
        }
        try {
            return PharmacyStaffRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new RuntimeException("Unknown role: " + role);
        }
    }

    private List<PharmacyPermission> parsePermissions(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<PharmacyPermission> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            try {
                out.add(PharmacyPermission.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception e) {
                throw new RuntimeException("Unknown permission: " + value);
            }
        }
        return out;
    }

    private String nextEmployeeId(Long pharmacyId) {
        long count = pharmacyStaffRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacyId).size();
        String candidate;
        int guard = 0;
        do {
            candidate = String.format("EMP-%05d", count + 1 + guard);
            guard++;
        } while (pharmacyStaffRepository.findByPharmacyIdAndEmployeeIdIgnoreCase(pharmacyId, candidate).isPresent() && guard < 1000);
        return candidate;
    }

    private String normalizeEmployeeId(String employeeId) {
        return employeeId == null ? null : employeeId.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash invite token", e);
        }
    }
}
