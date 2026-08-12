package com.tenahub.bot.service.impl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.tenahub.bot.dto.MiniAppAuthSendCodeRequestDTO;
import com.tenahub.bot.dto.MiniAppAuthVerifyCodeRequestDTO;
import com.tenahub.bot.dto.MiniAppAuthVerifyCodeResponseDTO;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.MiniAppPharmacyPhotosDTO;
import com.tenahub.bot.dto.MiniAppMedicinePhotosDTO;
import com.tenahub.bot.dto.MiniAppPharmacyDetailDTO;
import com.tenahub.bot.dto.MiniAppPhotoDTO;
import com.tenahub.bot.dto.MiniAppInventoryItemDTO;
import com.tenahub.bot.dto.MiniAppReservationCardDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmItemDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmItemResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationPreloadItemDTO;
import com.tenahub.bot.dto.MiniAppReservationPreloadResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationCreateRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationResponseDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.entity.MiniAppPhoneVerification;
import com.tenahub.bot.entity.MedicinePhoto;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyPhoto;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MiniAppPhoneVerificationRepository;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacyPhotoRepository;
import com.tenahub.bot.repository.MedicinePhotoRepository;
import com.tenahub.bot.service.MedicinePhotoService;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.MiniAppService;
import com.tenahub.bot.service.PharmacyPhotoService;
import com.tenahub.bot.service.TelegramWebAppAuthService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.SmsService;
import com.tenahub.bot.util.LocalizationService;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import com.tenahub.bot.util.TelegramClient;
import com.tenahub.bot.registration.MultiMedicineSearchSessionManager;
import com.tenahub.bot.registration.MultiReservationSessionManager;
import com.tenahub.bot.registration.ReservationSessionManager;

/**
 * Implementation of MiniAppService.
 * Provides read-only access to pharmacy photos for Mini App frontend.
 */
@Service
public class MiniAppServiceImpl implements MiniAppService {

    private static final Logger log = LoggerFactory.getLogger(MiniAppServiceImpl.class);

    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private enum SearchOption {
        NONE,
        NEAREST,
        CHEAPEST,
        HIGHEST_RATED,
        OPEN_NOW
    }

    private record SearchPreferences(SearchOption sortOption,
                                     boolean openNowOnly,
                                     boolean verifiedOnly,
                                     boolean prescriptionRequiredOnly,
                                     boolean noPrescriptionOnly,
                                     boolean explicitSelection) {
    }
    
    @Autowired
    private PharmacyRepository pharmacyRepository;
    
    @Autowired
    private PharmacyPhotoRepository pharmacyPhotoRepository;
    
    @Autowired
    private PharmacyPhotoService pharmacyPhotoService;

    @Autowired
    private PharmacyInventoryRepository pharmacyInventoryRepository;

    @Autowired
    private MedicinePhotoRepository medicinePhotoRepository;

    @Autowired
    private MedicinePhotoService medicinePhotoService;

    @Autowired
    private PharmacyService pharmacyService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private MedicineReservationRepository medicineReservationRepository;

    @Autowired
    private MiniAppPhoneVerificationRepository miniAppPhoneVerificationRepository;

    @Autowired
    private SmsService smsService;

    @Autowired
    private TelegramWebAppAuthService telegramWebAppAuthService;

    @Autowired
    private TelegramClient telegramClient;

    @Autowired
    private LocalizationService localizationService;
    
    @Value("${telegram.bot-token}")
    private String botToken;
    
    @Value("${telegram.api-url}")
    private String baseApiUrl;

    @Value("${tenahub.mini-app.auth.code-expiry-minutes:5}")
    private int otpCodeExpiryMinutes;

    @Value("${tenahub.mini-app.auth.verification-token-expiry-minutes:30}")
    private int verificationTokenExpiryMinutes;

    @Value("${tenahub.reservation.pending-timeout-minutes:20}")
    private int pendingReservationTimeoutMinutes;

    @Value("${tenahub.mini-app.confirm.allow-missing-telegram-user-id:false}")
    private boolean allowMissingTelegramUserId;

    @Value("${tenahub.mini-app.confirm.dev-telegram-user-id:0}")
    private long devTelegramUserId;
    
    private final RestTemplate restTemplate = new RestTemplate();

        @Override
        @Transactional
        public MiniAppOperationResponseDTO sendVerificationCode(MiniAppAuthSendCodeRequestDTO request) {
        if (request == null) {
            throw new RuntimeException("request body is required");
        }

        String normalizedPhone = normalizePhone(request.getPhone());
        String code = generateOtpCode();

        smsService.sendVerificationSms(normalizedPhone, code);

        miniAppPhoneVerificationRepository.save(MiniAppPhoneVerification.builder()
            .phone(normalizedPhone)
            .code(code)
            .codeExpiresAt(LocalDateTime.now().plusMinutes(otpCodeExpiryMinutes))
            .createdAt(LocalDateTime.now())
            .build());

        return MiniAppOperationResponseDTO.builder()
            .success(true)
            .message("Code sent")
            .build();
        }

        @Override
        @Transactional
        public MiniAppAuthVerifyCodeResponseDTO verifyCode(MiniAppAuthVerifyCodeRequestDTO request) {
        if (request == null) {
            throw new RuntimeException("request body is required");
        }

        String normalizedPhone = normalizePhone(request.getPhone());
        String code = normalizeCode(request.getCode());

        MiniAppPhoneVerification verification = miniAppPhoneVerificationRepository
            .findTopByPhoneAndCodeAndCodeUsedFalseAndCodeExpiresAtAfterOrderByCreatedAtDesc(
                normalizedPhone,
                code,
                LocalDateTime.now())
            .orElseThrow(() -> new RuntimeException("Invalid or expired code"));

        String verificationToken = generateVerificationToken();
        verification.setCodeUsed(true);
        verification.setVerificationToken(verificationToken);
        verification.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(verificationTokenExpiryMinutes));
        verification.setVerificationTokenUsed(false);
        verification.setVerifiedAt(LocalDateTime.now());
        miniAppPhoneVerificationRepository.save(verification);

        return MiniAppAuthVerifyCodeResponseDTO.builder()
            .success(true)
            .verified(true)
            .verificationToken(verificationToken)
            .build();
        }

        @Override
        @Transactional
        public MiniAppReservationConfirmResponseDTO confirmReservation(MiniAppReservationConfirmRequestDTO request) {
        if (request == null) {
            throw new RuntimeException("request body is required");
        }
        if (request.getPharmacyId() == null) {
            throw new RuntimeException("pharmacyId is required");
        }

        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedCustomerName = request.getCustomerName() == null || request.getCustomerName().isBlank()
            ? null
            : request.getCustomerName().trim();
        Long reservationOwnerTelegramId = resolveAuthenticatedUserId(
                request.getTelegramInitData(),
                request.getInitData(),
                request.getTelegramUserId(),
                true);

        if (isGroupedConfirmRequest(request)) {
            return confirmGroupedReservation(
                    request,
                    normalizedPhone,
                    normalizedCustomerName,
                    reservationOwnerTelegramId
            );
        }

        if (request.getMedicineId() == null) {
            throw new RuntimeException("medicineId is required");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new RuntimeException("quantity must be greater than 0");
        }

        Pharmacy pharmacy = pharmacyRepository.findById(request.getPharmacyId())
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        PharmacyInventory inventory = pharmacyInventoryRepository.findById(request.getMedicineId())
            .orElseThrow(() -> new RuntimeException("Medicine not found"));

        if (!request.getPharmacyId().equals(inventory.getPharmacyId())) {
            throw new RuntimeException("Medicine does not belong to the selected pharmacy");
        }

        MedicineReservation reservation = reservationService.createReservation(
            reservationOwnerTelegramId,
            request.getPharmacyId(),
            inventory.getMedicineName(),
            request.getQuantity(),
            normalizedPhone,
            normalizedCustomerName
        );

        reservation.setPendingExpiresAt(resolvePendingExpiryForNewReservation(reservation));
        reservation.setQrToken(generateQrToken());
        if (request.getNote() != null && !request.getNote().isBlank()) {
            reservation.setNote(request.getNote().trim());
        }
        medicineReservationRepository.save(reservation);
        System.out.println("[CONFIRM] reservationId=" + reservation.getId()
                + ", status=" + (reservation.getStatus() != null ? reservation.getStatus().name() : "null")
                + ", prescriptionRequired=" + reservation.isPrescriptionRequired()
                + ", queue=" + (reservation.isPrescriptionRequired() ? "prescription_review" : "pending_reservations")
                + ", note=" + reservation.getNote());
        clearMiniAppReservationSessions(reservationOwnerTelegramId);
        notifyReservationCreatedAfterCommit(reservation, normalizedPhone, normalizedCustomerName);

        return MiniAppReservationConfirmResponseDTO.builder()
            .reservationId(reservation.getId())
            .grouped(false)
            .reservationGroupId(reservation.getReservationGroupId())
            .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
            .prescriptionRequired(reservation.isPrescriptionRequired())
            .prescriptionReviewStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
            .prescriptionRejectionReason(reservation.getPrescriptionRejectionReason())
            .expiresAt(reservation.getPendingExpiresAt())
            .qrToken(reservation.getQrToken())
            .pharmacyName(pharmacy.getName())
            .medicineName(inventory.getMedicineName())
            .quantity(reservation.getRequestedQuantity())
            .items(List.of(buildConfirmItemResponse(reservation, inventory.getId())))
            .build();
        }

    @Override
    public List<MiniAppReservationCardDTO> getActiveReservations(Long telegramUserId) {
        Long resolvedTelegramUserId = null;
        try {
            resolvedTelegramUserId = requireTelegramUserId(telegramUserId);
        } catch (Exception e) {
            log.warn("[Service] getActiveReservations: Invalid or missing telegramUserId: {}", telegramUserId, e);
            throw e;
        }
        List<MedicineReservationStatus> activeStatuses = List.of(MedicineReservationStatus.PENDING, MedicineReservationStatus.APPROVED, MedicineReservationStatus.READY_FOR_PICKUP);
        List<MedicineReservation> reservations = List.of();
        try {
            reservations = medicineReservationRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(
                resolvedTelegramUserId, activeStatuses);
        } catch (Exception e) {
            log.error("[Service] getActiveReservations: Error fetching reservations for user {}: {}", resolvedTelegramUserId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch active reservations");
        }
        if (reservations == null) {
            reservations = List.of();
        }

        // Expire past-deadline reservations before building cards
        LocalDateTime now = LocalDateTime.now();
        List<MedicineReservation> stillActive = new java.util.ArrayList<>();
        for (MedicineReservation r : reservations) {
            if (r.getExpiresAt() != null && !r.getExpiresAt().isAfter(now)) {
                try {
                    if (r.getStatus() == MedicineReservationStatus.PENDING) {
                        reservationService.autoCancelPendingReservation(r.getId(), "AUTO_CANCELLED_PENDING_TIMEOUT");
                        log.info("[Service] getActiveReservations: Auto-cancelled pending reservation id={}", r.getId());
                    } else {
                        reservationService.expireReservation(r.getId());
                        log.info("[Service] getActiveReservations: Auto-expired reservation id={}", r.getId());
                    }
                } catch (Exception ex) {
                    log.warn("[Service] getActiveReservations: Failed to expire/cancel reservation id={}: {}", r.getId(), ex.getMessage());
                }
            } else {
                stillActive.add(r);
            }
        }
        reservations = stillActive;
        List<MiniAppReservationCardDTO> cards = toMiniAppReservationCards(reservations);
        log.info("[Service] getActiveReservations: userId={}, dbRows={}, cards={}", resolvedTelegramUserId, reservations.size(), cards.size());
        for (MiniAppReservationCardDTO card : cards) {
            log.info("[Service] ActiveCard: id={}, groupId={}, reservationStatus={}, prescriptionStatus={}, canShowQr={}, userFacingStage={}, rxRequired={}, expiresAt={}, pharmacyName={}, medicineName={}, qty={}",
                card.getReservationId(), card.getReservationGroupId(), card.getReservationStatus(),
                card.getPrescriptionStatus(), card.isCanShowQr(), card.getUserFacingStage(),
                card.isPrescriptionRequired(),
                card.getExpiresAt(), card.getPharmacyName(), card.getMedicineName(), card.getQuantity());
        }
        return cards;
    }

    @Override
    public List<MiniAppReservationCardDTO> getReservationHistory(Long telegramUserId) {
        Long resolvedTelegramUserId = requireTelegramUserId(telegramUserId);

        return medicineReservationRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(
                resolvedTelegramUserId,
                List.of(
                    MedicineReservationStatus.FULFILLED,
                    MedicineReservationStatus.EXPIRED,
                    MedicineReservationStatus.REJECTED,
                    MedicineReservationStatus.CANCELLED))
            .stream()
            .filter(reservation -> reservation.getHiddenFromUserAt() == null)
            .collect(Collectors.collectingAndThen(Collectors.toList(), this::toMiniAppReservationCards));
    }

    @Override
    public MiniAppOperationResponseDTO hideReservationFromHistory(Long reservationId, Long telegramUserId) {
        if (reservationId == null) {
            throw new RuntimeException("reservationId is required");
        }
        Long userId = requireTelegramUserId(telegramUserId);
        MedicineReservation reservation = medicineReservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getUserId() == null || !reservation.getUserId().equals(userId)) {
            throw new RuntimeException("Reservation does not belong to this user");
        }
        if (!isUserHistoryStatus(reservation.getStatus())) {
            throw new RuntimeException("Only completed reservations can be removed from history");
        }

        LocalDateTime hiddenAt = LocalDateTime.now();
        List<MedicineReservation> toHide = resolveHistoryHideTargets(reservation, userId);
        for (MedicineReservation item : toHide) {
            if (item.getHiddenFromUserAt() == null) {
                item.setHiddenFromUserAt(hiddenAt);
            }
        }
        medicineReservationRepository.saveAll(toHide);

        return MiniAppOperationResponseDTO.builder()
                .success(true)
                .message("Reservation removed from history.")
                .build();
    }

    @Override
    public MiniAppOperationResponseDTO clearReservationHistory(Long telegramUserId) {
        Long userId = requireTelegramUserId(telegramUserId);
        List<MedicineReservation> history = medicineReservationRepository.findByUserIdAndStatusIn(
                userId,
                List.of(
                        MedicineReservationStatus.FULFILLED,
                        MedicineReservationStatus.EXPIRED,
                        MedicineReservationStatus.REJECTED,
                        MedicineReservationStatus.CANCELLED));

        LocalDateTime hiddenAt = LocalDateTime.now();
        List<MedicineReservation> toHide = history.stream()
                .filter(reservation -> reservation.getHiddenFromUserAt() == null)
                .toList();
        for (MedicineReservation item : toHide) {
            item.setHiddenFromUserAt(hiddenAt);
        }
        if (!toHide.isEmpty()) {
            medicineReservationRepository.saveAll(toHide);
        }

        return MiniAppOperationResponseDTO.builder()
                .success(true)
                .message("Reservation history cleared.")
                .build();
    }

    private List<MedicineReservation> resolveHistoryHideTargets(MedicineReservation reservation, Long userId) {
        String groupId = reservation.getReservationGroupId();
        if (groupId == null || groupId.isBlank()) {
            return List.of(reservation);
        }

        return medicineReservationRepository.findByReservationGroupId(groupId).stream()
                .filter(item -> userId.equals(item.getUserId()))
                .filter(item -> isUserHistoryStatus(item.getStatus()))
                .toList();
    }

    private boolean isUserHistoryStatus(MedicineReservationStatus status) {
        return status == MedicineReservationStatus.FULFILLED
                || status == MedicineReservationStatus.EXPIRED
                || status == MedicineReservationStatus.REJECTED
                || status == MedicineReservationStatus.CANCELLED;
    }
    
    @Override
    public MiniAppPharmacyPhotosDTO getPharmacyPhotos(Long pharmacyId) {
        
        // Fetch pharmacy; throw 404 if not found
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found with ID: " + pharmacyId));
        
        // Fetch all photos for this pharmacy (ordered by main photo first, then sort order)
        List<PharmacyPhoto> photos = pharmacyPhotoService.listByPharmacyId(pharmacyId);
        
        // Convert to Mini App DTOs
        List<MiniAppPhotoDTO> photoDTOs = photos.stream()
            .map(photo -> MiniAppPhotoDTO.builder()
                .photoId(photo.getId())
                .fileId(photo.getFileId())
                .mainPhoto(photo.isMainPhoto())
                .sortOrder(photo.getSortOrder())
                .caption(photo.getCaption())
                .build())
            .collect(Collectors.toList());
        
        // Build response
        return MiniAppPharmacyPhotosDTO.builder()
            .pharmacyId(pharmacyId)
            .pharmacyName(pharmacy.getName())
            .photos(photoDTOs)
            .build();
    }
    
    @Override
    public byte[] downloadPharmacyPhoto(Long pharmacyId, Long photoId) {
        
        // Verify pharmacy exists
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found with ID: " + pharmacyId));
        
        // Fetch the specific photo
        PharmacyPhoto photo = pharmacyPhotoRepository.findById(photoId)
            .orElseThrow(() -> new RuntimeException("Photo not found with ID: " + photoId));
        
        // Verify photo belongs to the requested pharmacy
        if (!photo.getPharmacyId().equals(pharmacyId)) {
            throw new RuntimeException("Photo does not belong to pharmacy " + pharmacyId);
        }
        
        // Download file from Telegram
        try {
            String fileId = photo.getFileId();
            String apiUrl = baseApiUrl + "/bot" + botToken;
            
            // Step 1: Get file path from Telegram
            String getFileUrl = apiUrl + "/getFile?file_id=" + fileId;
            @SuppressWarnings("unchecked")
            Map<String, Object> getFileResponse = restTemplate.getForObject(getFileUrl, Map.class);
            
            if (getFileResponse == null || !getFileResponse.containsKey("result")) {
                throw new RuntimeException("Failed to get file info from Telegram");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) getFileResponse.get("result");
            String filePath = (String) result.get("file_path");
            
            if (filePath == null) {
                throw new RuntimeException("No file path returned by Telegram");
            }
            
            // Step 2: Download file from Telegram CDN
            String downloadUrl = baseApiUrl + "/file/bot" + botToken + "/" + filePath;
            byte[] fileData = restTemplate.getForObject(downloadUrl, byte[].class);
            
            if (fileData == null || fileData.length == 0) {
                throw new RuntimeException("Failed to download file data from Telegram");
            }
            
            return fileData;
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error downloading pharmacy photo: " + e.getMessage(), e);
        }
    }

    @Override
    public MiniAppMedicinePhotosDTO getMedicinePhotos(Long medicineId) {
        PharmacyInventory medicine = pharmacyInventoryRepository.findById(medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine not found with ID: " + medicineId));

        List<MedicinePhoto> photos = medicinePhotoService.listByMedicineId(medicineId);

        List<MiniAppPhotoDTO> photoDTOs = photos.stream()
                .map(photo -> MiniAppPhotoDTO.builder()
                        .photoId(photo.getId())
                        .fileId(photo.getTelegramFileId())
                        .mainPhoto(photo.isMainPhoto())
                        .sortOrder(photo.getSortOrder())
                        .caption(photo.getCaption())
                        .build())
                .collect(Collectors.toList());

        return MiniAppMedicinePhotosDTO.builder()
                .medicineId(medicineId)
                .pharmacyId(medicine.getPharmacyId())
                .medicineName(medicine.getMedicineName())
                .photos(photoDTOs)
                .build();
    }

    @Override
    public byte[] downloadMedicinePhoto(Long medicineId, Long photoId) {
        pharmacyInventoryRepository.findById(medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine not found with ID: " + medicineId));

        MedicinePhoto photo = medicinePhotoRepository.findByIdAndMedicineId(photoId, medicineId)
                .orElseThrow(() -> new RuntimeException("Medicine photo not found with ID: " + photoId));

        return medicinePhotoService.getImageBytesByTelegramFileId(photo.getTelegramFileId());
    }

    @Override
    public Long resolveMedicineId(Long pharmacyId, String medicineName) {
        if (pharmacyId == null) {
            throw new RuntimeException("pharmacyId is required");
        }
        if (medicineName == null || medicineName.isBlank()) {
            throw new RuntimeException("medicineName is required");
        }

        PharmacyInventory medicine = pharmacyInventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName.trim())
                .orElseThrow(() -> new RuntimeException("Medicine not found for pharmacy"));

        return medicine.getId();
    }

    @Override
    public MiniAppOperationResponseDTO cancelReservation(Long reservationId, Long telegramUserId) {
        if (reservationId == null) {
            throw new RuntimeException("reservationId is required");
        }
        Long userId = requireTelegramUserId(telegramUserId);
        log.info("[Service] cancelReservation: reservationId={}, userId={}", reservationId, userId);
        reservationService.cancelReservationByUser(userId, reservationId);
        return MiniAppOperationResponseDTO.builder()
                .success(true)
                .message("Reservation cancelled successfully.")
                .build();
    }

        @Override
        public MiniAppReservationPreloadResponseDTO getReservationPreload(Long pharmacyId, List<Long> medicineIds) {
        if (pharmacyId == null) {
            throw new RuntimeException("pharmacyId is required");
        }

        List<Long> requestedMedicineIds = medicineIds == null
            ? List.of()
            : medicineIds.stream()
                .filter(value -> value != null && value > 0)
                .distinct()
                .toList();

        if (requestedMedicineIds.isEmpty()) {
            throw new RuntimeException("at least one medicineId is required");
        }

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found with ID: " + pharmacyId));

        Map<Long, PharmacyInventory> inventoryById = pharmacyInventoryRepository.findByPharmacyId(pharmacyId).stream()
            .collect(Collectors.toMap(PharmacyInventory::getId, item -> item));

        List<MiniAppReservationPreloadItemDTO> items = requestedMedicineIds.stream()
            .map(inventoryById::get)
            .filter(item -> item != null)
            .map(item -> MiniAppReservationPreloadItemDTO.builder()
                .medicineId(item.getId())
                .medicineName(item.getMedicineName())
                .price(item.getPrice())
                .stockQuantity(item.getQuantity())
                .requiresPrescription(item.isRequiresPrescription())
                .currency(item.getCurrency())
                .build())
            .toList();

        List<Long> invalidMedicineIds = requestedMedicineIds.stream()
            .filter(medicineId -> !inventoryById.containsKey(medicineId))
            .toList();

        return MiniAppReservationPreloadResponseDTO.builder()
            .pharmacyId(pharmacy.getId())
            .pharmacyName(pharmacy.getName())
            .items(items)
            .invalidMedicineIds(invalidMedicineIds)
            .build();
        }

    @Override
    public List<PharmacyResponseDTO> search(String medicine,
                                            Double latitude,
                                            Double longitude,
                                            Long userId,
                                            String sort,
                                            String filter) {
        if (medicine == null || medicine.isBlank()) {
            throw new RuntimeException("medicine is required");
        }

        boolean hasBothCoordinates = latitude != null && longitude != null;
        List<PharmacyResponseDTO> results;
        if (hasBothCoordinates) {
            Long safeUserId = userId == null ? 0L : userId;
            results = pharmacyService.searchMedicineNearby(medicine.trim(), latitude, longitude, safeUserId);
        } else {
            results = pharmacyService.searchMedicine(medicine.trim());
        }

        return applySearchPreferences(results, sort, filter, hasBothCoordinates);
    }

    @Override
    public MiniAppPharmacyDetailDTO getPharmacyDetails(Long pharmacyId) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with ID: " + pharmacyId));

        List<MiniAppInventoryItemDTO> medicines = pharmacyInventoryRepository.findByPharmacyId(pharmacyId).stream()
                .map(item -> MiniAppInventoryItemDTO.builder()
                        .medicineId(item.getId())
                        .medicineName(item.getMedicineName())
                        .quantity(item.getQuantity())
                        .outOfStock(item.isOutOfStock() || item.getQuantity() == null || item.getQuantity() <= 0)
                    .requiresPrescription(item.isRequiresPrescription())
                        .price(item.getPrice())
                        .currency(item.getCurrency())
                        .build())
                .collect(Collectors.toList());

        boolean temporaryClosureActive = isTemporaryClosureActive(pharmacy);
        boolean openNow = !temporaryClosureActive && isOpenNow(pharmacy.getOpenTime(), pharmacy.getCloseTime());

        return MiniAppPharmacyDetailDTO.builder()
                .pharmacyId(pharmacy.getId())
                .name(pharmacy.getName())
                .city(pharmacy.getCity())
                .area(pharmacy.getArea())
                .phone(pharmacy.getPhone())
                .latitude(pharmacy.getLatitude())
                .longitude(pharmacy.getLongitude())
                .formattedAddress(pharmacy.getFormattedAddress())
                .landmark(pharmacy.getLandmark())
                .plusCode(pharmacy.getPlusCode())
                .rating(pharmacy.getRating())
                .approved(pharmacy.isApproved())
                .openNow(openNow)
                .openTime(pharmacy.getOpenTime() == null ? null : pharmacy.getOpenTime().toString())
                .closeTime(pharmacy.getCloseTime() == null ? null : pharmacy.getCloseTime().toString())
                .temporarilyClosed(temporaryClosureActive)
                .temporaryClosureReason(temporaryClosureActive ? pharmacy.getTemporaryClosureReason() : null)
                .medicines(medicines)
                .build();
    }

    @Override
    public MiniAppReservationResponseDTO createReservation(MiniAppReservationCreateRequestDTO request) {
        if (request == null) {
            throw new RuntimeException("request body is required");
        }
        Long userId = resolveAuthenticatedUserId(
                request.getTelegramInitData(),
                request.getInitData(),
                request.getUserId(),
                true);
        request.setUserId(userId);
        if (request.getPharmacyId() == null) {
            throw new RuntimeException("pharmacyId is required");
        }
        if (request.getMedicineName() == null || request.getMedicineName().isBlank()) {
            throw new RuntimeException("medicineName is required");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new RuntimeException("quantity must be greater than 0");
        }

        String normalizedPhone = request.getCustomerPhone() == null || request.getCustomerPhone().isBlank()
            ? null
            : normalizePhone(request.getCustomerPhone());

        MedicineReservation reservation = reservationService.createReservation(
                request.getUserId(),
                request.getPharmacyId(),
                request.getMedicineName().trim(),
                request.getQuantity(),
            normalizedPhone,
                request.getCustomerName()
        );

        return MiniAppReservationResponseDTO.builder()
                .reservationId(reservation.getId())
                .pharmacyId(reservation.getPharmacyId())
                .userId(reservation.getUserId())
                .medicineName(reservation.getMedicineName())
                .requestedQuantity(reservation.getRequestedQuantity())
                .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
            .prescriptionRequired(reservation.isPrescriptionRequired())
            .prescriptionReviewStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
            .prescriptionRejectionReason(reservation.getPrescriptionRejectionReason())
                .createdAt(reservation.getCreatedAt())
                .expiresAt(reservation.getExpiresAt())
                .customerPhone(reservation.getCustomerPhone())
                .customerName(reservation.getCustomerName())
                .build();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new RuntimeException("phone is required");
        }

        String normalized = phone.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");

        if (!E164_PHONE_PATTERN.matcher(normalized).matches()) {
            throw new RuntimeException("phone must be a valid E.164 number");
        }

        return normalized;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new RuntimeException("code is required");
        }

        String normalized = code.trim();
        if (!normalized.matches("^\\d{6}$")) {
            throw new RuntimeException("code must be a 6-digit value");
        }
        return normalized;
    }

    private Long resolveAuthenticatedUserId(String telegramInitData,
                                            String initData,
                                            Long claimedUserId,
                                            boolean required) {
        String resolvedInitData = firstNonBlank(telegramInitData, initData);
        if (resolvedInitData != null) {
            return telegramWebAppAuthService.parseUserId(resolvedInitData);
        }
        if (required) {
            throw new MiniAppAuthException("Telegram initData is required");
        }
        return resolveReservationOwnerTelegramUserId(claimedUserId);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private Long resolveReservationOwnerTelegramUserId(Long telegramUserId) {
        if (telegramUserId != null && telegramUserId > 0) {
            return telegramUserId;
        }

        if (allowMissingTelegramUserId && devTelegramUserId > 0) {
            return devTelegramUserId;
        }

        // No valid Telegram user ID available (e.g. mini app opened outside Telegram).
        // Allow the reservation to proceed; Telegram bot notifications will be skipped.
        return null;
    }

    private String normalizeVerificationToken(String verificationToken) {
        if (verificationToken == null || verificationToken.isBlank()) {
            throw new RuntimeException("verificationToken is required");
        }
        return verificationToken.trim();
    }

    private String generateOtpCode() {
        return String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String generateVerificationToken() {
        return "VERIF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private String generateQrToken() {
        return "RESV_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private boolean isGroupedConfirmRequest(MiniAppReservationConfirmRequestDTO request) {
        return request.getItems() != null && !request.getItems().isEmpty();
    }

    private MiniAppReservationConfirmResponseDTO confirmGroupedReservation(MiniAppReservationConfirmRequestDTO request,
                                                                           String normalizedPhone,
                                                                           String normalizedCustomerName,
                                                                           Long reservationOwnerTelegramId) {
        Pharmacy pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        Map<String, Integer> medicineQuantities = new LinkedHashMap<>();
        Map<String, Long> medicineIdsByCanonicalName = new LinkedHashMap<>();

        for (MiniAppReservationConfirmItemDTO item : request.getItems()) {
            if (item == null || item.getMedicineId() == null) {
                throw new RuntimeException("each grouped reservation item must include medicineId");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new RuntimeException("each grouped reservation item quantity must be greater than 0");
            }

            PharmacyInventory inventory = pharmacyInventoryRepository.findById(item.getMedicineId())
                    .orElseThrow(() -> new RuntimeException("Medicine not found"));

            if (!request.getPharmacyId().equals(inventory.getPharmacyId())) {
                throw new RuntimeException("Medicine does not belong to the selected pharmacy");
            }

            String canonicalMedicineName = MedicineSearchNormalizer.normalizeToEnglishCanonical(inventory.getMedicineName());
            medicineQuantities.merge(canonicalMedicineName, item.getQuantity(), Integer::sum);
            medicineIdsByCanonicalName.put(canonicalMedicineName, inventory.getId());
        }

        if (medicineQuantities.isEmpty()) {
            throw new RuntimeException("at least one grouped reservation item is required");
        }

        List<MedicineReservation> groupedReservations = reservationService.createReservationGroup(
                reservationOwnerTelegramId,
                request.getPharmacyId(),
                medicineQuantities,
                normalizedPhone,
                normalizedCustomerName
        );

        if (groupedReservations.isEmpty()) {
            throw new RuntimeException("No reservations created for reservation group");
        }

        LocalDateTime pendingExpiresAt = resolvePendingExpiryForNewReservation(groupedReservations);
        String groupQrToken = generateQrToken();
        String normalizedNote = (request.getNote() != null && !request.getNote().isBlank()) ? request.getNote().trim() : null;
        for (MedicineReservation reservation : groupedReservations) {
            reservation.setPendingExpiresAt(pendingExpiresAt);
            reservation.setQrToken(groupQrToken);
            if (normalizedNote != null) {
                reservation.setNote(normalizedNote);
            }
            medicineReservationRepository.save(reservation);
        }

        clearMiniAppReservationSessions(reservationOwnerTelegramId);
        notifyGroupedReservationCreatedAfterCommit(groupedReservations);

        List<MiniAppReservationConfirmItemResponseDTO> responseItems = groupedReservations.stream()
                .map(reservation -> buildConfirmItemResponse(
                        reservation,
                        medicineIdsByCanonicalName.get(MedicineSearchNormalizer.normalizeToEnglishCanonical(reservation.getMedicineName()))
                ))
                .toList();

        return MiniAppReservationConfirmResponseDTO.builder()
                .reservationGroupId(groupedReservations.get(0).getReservationGroupId())
                .grouped(true)
            .groupedStatus(resolveGroupedStatus(groupedReservations))
                .status(groupedReservations.get(0).getStatus() == null ? null : groupedReservations.get(0).getStatus().name())
                .prescriptionRequired(groupedReservations.stream().anyMatch(MedicineReservation::isPrescriptionRequired))
                .prescriptionReviewStatus(resolveGroupedPrescriptionStatus(groupedReservations))
                .prescriptionRejectionReason(resolveGroupedPrescriptionRejectionReason(groupedReservations))
                .expiresAt(pendingExpiresAt)
            .qrToken(groupedReservations.get(0).getQrToken())
                .pharmacyName(pharmacy.getName())
                .items(responseItems)
                .build();
    }

    private MiniAppReservationConfirmItemResponseDTO buildConfirmItemResponse(MedicineReservation reservation, Long medicineId) {
        return MiniAppReservationConfirmItemResponseDTO.builder()
                .reservationId(reservation.getId())
                .medicineId(medicineId)
                .medicineName(reservation.getMedicineName())
                .quantity(reservation.getRequestedQuantity())
                .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
                .prescriptionRequired(reservation.isPrescriptionRequired())
                .prescriptionReviewStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
                .prescriptionRejectionReason(reservation.getPrescriptionRejectionReason())
                .expiresAt(resolveReservationExpiresAt(reservation))
                .qrToken(reservation.getQrToken())
                .build();
    }

    private void clearMiniAppReservationSessions(Long telegramUserId) {
        if (telegramUserId == null) {
            return;
        }

        ReservationSessionManager.remove(telegramUserId);
        MultiReservationSessionManager.remove(telegramUserId);
        MultiMedicineSearchSessionManager.remove(telegramUserId);
    }

    private void notifyReservationCreatedAfterCommit(MedicineReservation reservation,
                                                     String customerPhone,
                                                     String customerName) {
        if (reservation == null) {
            return;
        }

        // Pharmacy Telegram alert is sent from ReservationServiceImpl after save.
        // Prescription-required: Mini App handles upload; skip the user Telegram prompt.
        if (requiresPrescriptionUploadBeforePharmacyNotification(reservation)) {
            return;
        }

        Runnable notifier = () -> {
            try {
                if (reservation.getUserId() != null && reservation.getUserId() > 0) {
                    telegramClient.sendMessage(
                        reservation.getUserId(),
                        localizationService.text(
                            reservation.getUserId(),
                            "reservation_contact_sent",
                            reservation.getMedicineName(),
                            reservation.getRequestedQuantity(),
                            customerName,
                            customerPhone
                        ) + "\n\u23F1 Auto-cancels in " + pendingReservationTimeoutMinutes + " minutes if not approved."
                    );
                }
            } catch (Exception notificationError) {
                log.warn("Mini app user reservation confirmation failed for reservation {}: {}",
                        reservation.getId(), notificationError.getMessage());
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifier.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifier.run();
            }
        });
    }

    private void notifyReservationCreated(MedicineReservation reservation,
                                          String customerPhone,
                                          String customerName) {
        // Kept for potential direct (non-transactional) callers; delegates to the main flow.
        notifyReservationCreatedAfterCommit(reservation, customerPhone, customerName);
    }

    private void notifyGroupedReservationCreatedAfterCommit(List<MedicineReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifyGroupedReservationCreated(reservations);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyGroupedReservationCreated(reservations);
            }
        });
    }

    private void notifyGroupedReservationCreated(List<MedicineReservation> reservations) {
        try {
            String groupId = reservations.get(0).getReservationGroupId();
            Long userId = reservations.get(0).getUserId();
            if (userId != null && userId > 0 && groupId != null && !groupId.isBlank()) {
                telegramClient.sendMultiReserveGroupedConfirmation(userId, groupId, reservations);
            }
        } catch (Exception notificationError) {
            log.warn("Mini app grouped reservation user confirmation failed for group {}: {}",
                    reservations.get(0).getReservationGroupId(), notificationError.getMessage());
        }
    }

    private Long requireTelegramUserId(Long telegramUserId) {
        if (telegramUserId != null) {
            return telegramUserId;
        }
        // Dev fallback if enabled
        if (allowMissingTelegramUserId && devTelegramUserId > 0) {
            return devTelegramUserId;
        }
        throw new RuntimeException("telegramUserId query parameter is required");
    }

    private MiniAppReservationCardDTO toMiniAppReservationCard(MedicineReservation reservation) {
        Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId()).orElse(null);
        PharmacyInventory inventory = pharmacyInventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(reservation.getPharmacyId(), reservation.getMedicineName())
            .orElse(null);

        return MiniAppReservationCardDTO.builder()
            .reservationId(reservation.getId())
            .reservationGroupId(reservation.getReservationGroupId())
            .grouped(false)
            .groupedStatus(reservation.getStatus() == null ? null : reservation.getStatus().name())
            .prescriptionRequired(reservation.isPrescriptionRequired())
            .prescriptionStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
            .prescriptionReviewStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
            .prescriptionStatusLabel(buildPrescriptionStatusLabel(
                reservation.isPrescriptionRequired(),
                reservation.getPrescriptionReviewStatus()))
            .prescriptionRejectionReason(reservation.getPrescriptionRejectionReason())
            .pharmacyId(reservation.getPharmacyId())
            .pharmacyName(pharmacy == null ? null : pharmacy.getName())
            .medicineId(inventory == null ? null : inventory.getId())
            .medicineName(reservation.getMedicineName())
            .quantity(reservation.getRequestedQuantity())
                .reservationStatus(reservation.getStatus() == null ? null : reservation.getStatus().name())
            .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
            .reservationStatusLabel(buildReservationStatusLabel(
                reservation.getStatus(),
                reservation.isPrescriptionRequired(),
                reservation.getPrescriptionReviewStatus()))
            .readyForPickup(isReadyForPickup(
                reservation.getStatus(),
                reservation.isPrescriptionRequired(),
                reservation.getPrescriptionReviewStatus()))
                .canShowQr(canShowQr(
                    reservation.getStatus(),
                    reservation.isPrescriptionRequired(),
                    reservation.getPrescriptionReviewStatus()))
            .showQrCode(shouldShowQrCode(
                reservation.getStatus(),
                reservation.isPrescriptionRequired(),
                reservation.getPrescriptionReviewStatus(),
                reservation.getQrToken()))
            .userFacingStage(deriveUserFacingStage(
                reservation.getStatus(),
                reservation.isPrescriptionRequired(),
                reservation.getPrescriptionReviewStatus()))
                .holdUntil(resolveReservationExpiresAt(reservation))
            .expiresAt(resolveReservationExpiresAt(reservation))
            .qrToken(resolveQrToken(
                reservation.getStatus(),
                reservation.isPrescriptionRequired(),
                reservation.getPrescriptionReviewStatus(),
                reservation.getQrToken()))
            .createdAt(reservation.getCreatedAt())
            .phone(reservation.getCustomerPhone())
            .items(List.of(buildConfirmItemResponse(reservation, inventory == null ? null : inventory.getId())))
            .build();
    }

    private List<MiniAppReservationCardDTO> toMiniAppReservationCards(List<MedicineReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        Map<String, List<MedicineReservation>> groupedReservations = reservations.stream()
                .collect(Collectors.groupingBy(reservation -> {
                    if (reservation.getReservationGroupId() != null && !reservation.getReservationGroupId().isBlank()) {
                        return reservation.getReservationGroupId();
                    }
                    return "solo_" + reservation.getId();
                }, LinkedHashMap::new, Collectors.toList()));

        return groupedReservations.values().stream()
                .map(group -> {
                    MedicineReservation first = group.get(0);
                    if (group.size() == 1 && (first.getReservationGroupId() == null || first.getReservationGroupId().isBlank())) {
                        return toMiniAppReservationCard(first);
                    }

                    Pharmacy pharmacy = pharmacyRepository.findById(first.getPharmacyId()).orElse(null);
                    List<MiniAppReservationConfirmItemResponseDTO> items = group.stream()
                            .map(reservation -> {
                                PharmacyInventory inventory = pharmacyInventoryRepository
                                        .findByPharmacyIdAndMedicineNameIgnoreCase(reservation.getPharmacyId(), reservation.getMedicineName())
                                        .orElse(null);
                                return buildConfirmItemResponse(reservation, inventory == null ? null : inventory.getId());
                            })
                            .toList();

                    int totalQuantity = group.stream()
                            .map(MedicineReservation::getRequestedQuantity)
                            .filter(quantity -> quantity != null)
                            .mapToInt(Integer::intValue)
                            .sum();

                    return MiniAppReservationCardDTO.builder()
                            .reservationId(first.getId())
                            .reservationGroupId(first.getReservationGroupId())
                            .grouped(true)
                            .groupedStatus(resolveGroupedStatus(group))
                            .prescriptionRequired(group.stream().anyMatch(MedicineReservation::isPrescriptionRequired))
                            .prescriptionStatus(resolveGroupedPrescriptionStatus(group))
                            .prescriptionReviewStatus(resolveGroupedPrescriptionStatus(group))
                            .prescriptionStatusLabel(buildPrescriptionStatusLabel(
                                    group.stream().anyMatch(MedicineReservation::isPrescriptionRequired),
                                    parsePrescriptionStatus(resolveGroupedPrescriptionStatus(group))))
                            .prescriptionRejectionReason(resolveGroupedPrescriptionRejectionReason(group))
                            .pharmacyId(first.getPharmacyId())
                            .pharmacyName(pharmacy == null ? null : pharmacy.getName())
                            .medicineName(group.size() + " medicines")
                            .quantity(totalQuantity)
                                .reservationStatus(resolveGroupedStatus(group))
                            .status(resolveGroupedStatus(group))
                            .reservationStatusLabel(buildReservationStatusLabel(
                                    parseReservationStatus(resolveGroupedStatus(group)),
                                    group.stream().anyMatch(MedicineReservation::isPrescriptionRequired),
                                    parsePrescriptionStatus(resolveGroupedPrescriptionStatus(group))))
                            .readyForPickup(isReadyForPickup(
                                    parseReservationStatus(resolveGroupedStatus(group)),
                                    group.stream().anyMatch(MedicineReservation::isPrescriptionRequired),
                                    parsePrescriptionStatus(resolveGroupedPrescriptionStatus(group))))
                                .canShowQr(canShowQr(
                                    parseReservationStatus(resolveGroupedStatus(group)),
                                    group.stream().anyMatch(MedicineReservation::isPrescriptionRequired),
                                    parsePrescriptionStatus(resolveGroupedPrescriptionStatus(group))))
                            .showQrCode(shouldShowQrCode(
                                    parseReservationStatus(resolveGroupedStatus(group)),
                                    group.stream().anyMatch(MedicineReservation::isPrescriptionRequired),
                                    parsePrescriptionStatus(resolveGroupedPrescriptionStatus(group)),
                                    first.getQrToken()))
                            .userFacingStage(deriveUserFacingStage(
                                    parseReservationStatus(resolveGroupedStatus(group)),
                                    group.stream().anyMatch(MedicineReservation::isPrescriptionRequired),
                                    parsePrescriptionStatus(resolveGroupedPrescriptionStatus(group))))
                                .holdUntil(resolveGroupedExpiresAt(group))
                            .expiresAt(resolveGroupedExpiresAt(group))
                            .qrToken(resolveQrToken(
                                    parseReservationStatus(resolveGroupedStatus(group)),
                                    group.stream().anyMatch(MedicineReservation::isPrescriptionRequired),
                                    parsePrescriptionStatus(resolveGroupedPrescriptionStatus(group)),
                                    first.getQrToken()))
                            .createdAt(group.stream().map(MedicineReservation::getCreatedAt).filter(value -> value != null).min(LocalDateTime::compareTo).orElse(first.getCreatedAt()))
                            .phone(first.getCustomerPhone())
                            .items(items)
                            .build();
                })
                .toList();
    }

    private String resolveQrToken(MedicineReservationStatus reservationStatus,
                                  boolean prescriptionRequired,
                                  PrescriptionReviewStatus prescriptionStatus,
                                  String qrToken) {
        return shouldShowQrCode(reservationStatus, prescriptionRequired, prescriptionStatus, qrToken) ? qrToken : null;
    }

    private boolean shouldShowQrCode(MedicineReservationStatus reservationStatus,
                                     boolean prescriptionRequired,
                                     PrescriptionReviewStatus prescriptionStatus,
                                     String qrToken) {
        return canShowQr(reservationStatus, prescriptionRequired, prescriptionStatus)
                && qrToken != null && !qrToken.isBlank();
    }

    private boolean canShowQr(MedicineReservationStatus reservationStatus,
                              boolean prescriptionRequired,
                              PrescriptionReviewStatus prescriptionStatus) {
        boolean rxOk = !prescriptionRequired || prescriptionStatus == PrescriptionReviewStatus.APPROVED;
        boolean resOk = reservationStatus == MedicineReservationStatus.APPROVED
                || reservationStatus == MedicineReservationStatus.READY_FOR_PICKUP;
        return rxOk && resOk;
    }

    private boolean isReadyForPickup(MedicineReservationStatus reservationStatus,
                                     boolean prescriptionRequired,
                                     PrescriptionReviewStatus prescriptionStatus) {
        return canShowQr(reservationStatus, prescriptionRequired, prescriptionStatus);
    }

    private String buildPrescriptionStatusLabel(boolean prescriptionRequired,
                                                PrescriptionReviewStatus prescriptionStatus) {
        if (!prescriptionRequired) {
            return "Not required";
        }

        PrescriptionReviewStatus resolvedStatus = prescriptionStatus == null
                ? PrescriptionReviewStatus.UPLOAD_REQUIRED
                : prescriptionStatus;

        return switch (resolvedStatus) {
            case UPLOAD_REQUIRED -> "Prescription required - upload needed";
            case PENDING_REVIEW -> "Prescription under review";
            case APPROVED -> "Prescription approved";
            case REJECTED -> "Prescription rejected";
            case NOT_REQUIRED -> "Not required";
        };
    }

    private String buildReservationStatusLabel(MedicineReservationStatus reservationStatus,
                                               boolean prescriptionRequired,
                                               PrescriptionReviewStatus prescriptionStatus) {
        if (prescriptionRequired && prescriptionStatus == PrescriptionReviewStatus.REJECTED) {
            return "Closed - prescription rejected";
        }
        if (reservationStatus == null) {
            return null;
        }
        if (reservationStatus == MedicineReservationStatus.REJECTED) {
            return "Rejected";
        }
        if (reservationStatus == MedicineReservationStatus.CANCELLED) {
            return "Cancelled";
        }
        if (reservationStatus == MedicineReservationStatus.EXPIRED) {
            return "Expired";
        }
        if (reservationStatus == MedicineReservationStatus.FULFILLED) {
            return "Fulfilled";
        }
        if (reservationStatus == MedicineReservationStatus.READY_FOR_PICKUP) {
            return "Ready for pickup";
        }
        if (isReadyForPickup(reservationStatus, prescriptionRequired, prescriptionStatus)) {
            return "Ready for pickup";
        }
        if (reservationStatus == MedicineReservationStatus.APPROVED) {
            return "Approved";
        }
        if (prescriptionRequired) {
            if (prescriptionStatus == PrescriptionReviewStatus.APPROVED) {
                return "Waiting for pharmacy reservation approval";
            }
            if (prescriptionStatus == PrescriptionReviewStatus.PENDING_REVIEW) {
                return "Waiting for prescription review";
            }
            if (prescriptionStatus == PrescriptionReviewStatus.UPLOAD_REQUIRED || prescriptionStatus == null) {
                return "Waiting for prescription upload";
            }
        }
        return reservationStatus == MedicineReservationStatus.PENDING ? "Waiting for pharmacy reservation approval" : reservationStatus.name();
    }

    private MedicineReservationStatus parseReservationStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return MedicineReservationStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private PrescriptionReviewStatus parsePrescriptionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PrescriptionReviewStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String deriveUserFacingStage(MedicineReservationStatus reservationStatus,
                                         boolean prescriptionRequired,
                                         PrescriptionReviewStatus prescriptionStatus) {
        if (reservationStatus == MedicineReservationStatus.FULFILLED) {
            return "COMPLETE";
        }
        if (reservationStatus == MedicineReservationStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (reservationStatus == MedicineReservationStatus.EXPIRED) {
            return "EXPIRED";
        }
        if (reservationStatus == MedicineReservationStatus.REJECTED
                || (prescriptionRequired && prescriptionStatus == PrescriptionReviewStatus.REJECTED)) {
            return "REJECTED";
        }
        boolean rxApproved = !prescriptionRequired || prescriptionStatus == PrescriptionReviewStatus.APPROVED;
        if (rxApproved
                && (reservationStatus == MedicineReservationStatus.APPROVED
                    || reservationStatus == MedicineReservationStatus.READY_FOR_PICKUP)) {
            return "READY_FOR_PICKUP";
        }
        if (prescriptionRequired && prescriptionStatus == PrescriptionReviewStatus.APPROVED
                && reservationStatus == MedicineReservationStatus.PENDING) {
            return "WAITING_RESERVATION_APPROVAL";
        }
        if (prescriptionRequired && prescriptionStatus == PrescriptionReviewStatus.PENDING_REVIEW) {
            return "PRESCRIPTION_REVIEW";
        }
        if (prescriptionRequired
                && (prescriptionStatus == PrescriptionReviewStatus.UPLOAD_REQUIRED || prescriptionStatus == null)) {
            return "UPLOAD_PRESCRIPTION";
        }
        return "RESERVED";
    }

    private LocalDateTime resolveGroupedExpiresAt(List<MedicineReservation> reservations) {
        return reservations.stream()
                .map(this::resolveReservationExpiresAt)
                .filter(value -> value != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private String resolveGroupedStatus(List<MedicineReservation> reservations) {
        LinkedHashSet<String> statuses = reservations.stream()
                .map(MedicineReservation::getStatus)
                .filter(value -> value != null)
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (statuses.isEmpty()) {
            return null;
        }

        if (statuses.size() == 1) {
            return statuses.iterator().next();
        }

        return "MIXED";
    }

    private String resolveGroupedPrescriptionStatus(List<MedicineReservation> reservations) {
        List<MedicineReservation> requiredReservations = reservations.stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .toList();

        if (requiredReservations.isEmpty()) {
            return PrescriptionReviewStatus.NOT_REQUIRED.name();
        }
        if (requiredReservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.REJECTED)) {
            return PrescriptionReviewStatus.REJECTED.name();
        }
        if (requiredReservations.stream().allMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.APPROVED)) {
            return PrescriptionReviewStatus.APPROVED.name();
        }
        if (requiredReservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.UPLOAD_REQUIRED)) {
            return PrescriptionReviewStatus.UPLOAD_REQUIRED.name();
        }
        return PrescriptionReviewStatus.PENDING_REVIEW.name();
    }

    private String resolveGroupedPrescriptionRejectionReason(List<MedicineReservation> reservations) {
        return reservations.stream()
                .map(MedicineReservation::getPrescriptionRejectionReason)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private LocalDateTime resolveReservationExpiresAt(MedicineReservation reservation) {
        if (reservation == null) {
            return null;
        }
        return reservation.getPendingExpiresAt() != null
            ? reservation.getPendingExpiresAt()
            : reservation.getExpiresAt();
    }

    private LocalDateTime resolvePendingExpiryForNewReservation(MedicineReservation reservation) {
        if (reservation == null || requiresPrescriptionUploadBeforePharmacyNotification(reservation)) {
            return null;
        }
        return LocalDateTime.now().plusMinutes(pendingReservationTimeoutMinutes);
    }

    private LocalDateTime resolvePendingExpiryForNewReservation(List<MedicineReservation> reservations) {
        if (reservations == null || reservations.isEmpty() || requiresPrescriptionUploadBeforePharmacyNotification(reservations)) {
            return null;
        }
        return LocalDateTime.now().plusMinutes(pendingReservationTimeoutMinutes);
    }

    private boolean requiresPrescriptionUploadBeforePharmacyNotification(MedicineReservation reservation) {
        return reservation != null
                && reservation.isPrescriptionRequired()
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.UPLOAD_REQUIRED;
    }

    private boolean requiresPrescriptionUploadBeforePharmacyNotification(List<MedicineReservation> reservations) {
        return reservations != null && reservations.stream().anyMatch(this::requiresPrescriptionUploadBeforePharmacyNotification);
    }

    private boolean isOpenNow(LocalTime open, LocalTime close) {
        if (open == null || close == null) {
            return false;
        }

        LocalTime now = LocalTime.now();
        if (close.equals(open)) {
            return true;
        }
        if (close.isAfter(open)) {
            return !now.isBefore(open) && !now.isAfter(close);
        }
        return !now.isBefore(open) || !now.isAfter(close);
    }

    private boolean isTemporaryClosureActive(Pharmacy pharmacy) {
        if (pharmacy == null || !pharmacy.isTemporarilyClosed()) {
            return false;
        }
        return pharmacy.getTemporaryClosedUntil() == null
                || pharmacy.getTemporaryClosedUntil().isAfter(java.time.LocalDateTime.now());
    }

    private List<PharmacyResponseDTO> applySearchPreferences(List<PharmacyResponseDTO> results,
                                                             String sort,
                                                             String filter,
                                                             boolean hasLocation) {
        SearchPreferences preferences = resolveSearchPreferences(sort, filter);
        if (!preferences.explicitSelection()) {
            return results;
        }

        java.util.stream.Stream<PharmacyResponseDTO> stream = results.stream();
        if (preferences.openNowOnly()) {
            stream = stream.filter(PharmacyResponseDTO::isOpenNow);
        }
        if (preferences.verifiedOnly()) {
            stream = stream.filter(PharmacyResponseDTO::isVerified);
        }
        if (preferences.prescriptionRequiredOnly() && !preferences.noPrescriptionOnly()) {
            stream = stream.filter(PharmacyResponseDTO::isRequiresPrescription);
        } else if (preferences.noPrescriptionOnly() && !preferences.prescriptionRequiredOnly()) {
            stream = stream.filter(item -> !item.isRequiresPrescription());
        }

        Comparator<PharmacyResponseDTO> comparator = buildComparator(preferences.sortOption(), hasLocation);
        if (comparator == null) {
            return stream.toList();
        }

        return stream.sorted(comparator).toList();
    }

    private SearchPreferences resolveSearchPreferences(String sort, String filter) {
        SearchOption sortOption = normalizeSearchOption(sort);
        SearchOption filterOption = normalizeSearchOption(filter);
        String mergedRawFilters = mergeRawFilters(sort, filter);

        boolean verifiedOnly = hasVerifiedOnlyFilter(mergedRawFilters);
        boolean prescriptionRequiredOnly = hasPrescriptionRequiredFilter(mergedRawFilters);
        boolean noPrescriptionOnly = hasNoPrescriptionFilter(mergedRawFilters);

        boolean explicitSelection = sortOption != SearchOption.NONE
            || filterOption != SearchOption.NONE
            || verifiedOnly
            || prescriptionRequiredOnly
            || noPrescriptionOnly;
        boolean openNowOnly = sortOption == SearchOption.OPEN_NOW || filterOption == SearchOption.OPEN_NOW;

        if (sortOption == SearchOption.OPEN_NOW) {
            sortOption = SearchOption.NONE;
        }
        if (filterOption == SearchOption.OPEN_NOW) {
            filterOption = SearchOption.NONE;
        }
        if (sortOption == SearchOption.NONE) {
            sortOption = filterOption;
        }
        if (sortOption == SearchOption.NONE && openNowOnly) {
            sortOption = SearchOption.NEAREST;
        }

        return new SearchPreferences(
                sortOption,
                openNowOnly,
                verifiedOnly,
                prescriptionRequiredOnly,
                noPrescriptionOnly,
                explicitSelection);
    }

    private String mergeRawFilters(String sort, String filter) {
        String first = sort == null ? "" : sort;
        String second = filter == null ? "" : filter;
        return (first + " " + second).toLowerCase(Locale.ROOT);
    }

    private boolean hasVerifiedOnlyFilter(String mergedRawFilters) {
        String normalizedLettersOnly = mergedRawFilters.replaceAll("[^a-z]", "");
        return normalizedLettersOnly.contains("verified")
                || normalizedLettersOnly.contains("verifiedonly");
    }

    private boolean hasPrescriptionRequiredFilter(String mergedRawFilters) {
        String normalizedLettersOnly = mergedRawFilters.replaceAll("[^a-z]", "");
        return normalizedLettersOnly.contains("prescriptionrequired")
                || normalizedLettersOnly.contains("requiresprescription")
                || normalizedLettersOnly.contains("rxrequired");
    }

    private boolean hasNoPrescriptionFilter(String mergedRawFilters) {
        String normalizedLettersOnly = mergedRawFilters.replaceAll("[^a-z]", "");
        return normalizedLettersOnly.contains("noprescription")
                || normalizedLettersOnly.contains("withoutprescription")
                || normalizedLettersOnly.contains("nonprescription")
                || normalizedLettersOnly.contains("prescriptionnotrequired");
    }

    private SearchOption normalizeSearchOption(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SearchOption.NONE;
        }

        String normalized = rawValue.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");

        return switch (normalized) {
            case "nearest" -> SearchOption.NEAREST;
            case "cheapest" -> SearchOption.CHEAPEST;
            case "highestrated" -> SearchOption.HIGHEST_RATED;
            case "opennow", "open" -> SearchOption.OPEN_NOW;
            default -> SearchOption.NONE;
        };
    }

    private Comparator<PharmacyResponseDTO> buildComparator(SearchOption sortOption, boolean hasLocation) {
        Comparator<PharmacyResponseDTO> stockFirst = Comparator.comparing(PharmacyResponseDTO::isOutOfStock);
        Comparator<PharmacyResponseDTO> ratingDesc = Comparator.comparingDouble(PharmacyResponseDTO::getRating).reversed();
        Comparator<PharmacyResponseDTO> priceAsc = Comparator.comparing(
                PharmacyResponseDTO::getPrice,
                Comparator.nullsLast(java.math.BigDecimal::compareTo));

        return switch (sortOption) {
            case NONE -> null;
            case NEAREST -> hasLocation
                    ? stockFirst
                        .thenComparingDouble(PharmacyResponseDTO::getDistance)
                        .thenComparing(ratingDesc)
                    : stockFirst.thenComparing(ratingDesc);
            case CHEAPEST -> stockFirst
                    .thenComparing(priceAsc)
                    .thenComparing(hasLocation
                            ? Comparator.comparingDouble(PharmacyResponseDTO::getDistance)
                            : ratingDesc);
            case HIGHEST_RATED -> stockFirst
                    .thenComparing(ratingDesc)
                    .thenComparing(hasLocation
                            ? Comparator.comparingDouble(PharmacyResponseDTO::getDistance)
                            : priceAsc);
            case OPEN_NOW -> hasLocation
                    ? stockFirst.thenComparingDouble(PharmacyResponseDTO::getDistance)
                    : stockFirst.thenComparing(ratingDesc);
        };
    }
}
