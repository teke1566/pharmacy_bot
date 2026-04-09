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
import com.tenahub.bot.service.MiniAppService;
import com.tenahub.bot.service.PharmacyPhotoService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.ReservationWorkflowService;
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

    private record SearchPreferences(SearchOption sortOption, boolean openNowOnly, boolean explicitSelection) {
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
    private ReservationWorkflowService reservationWorkflowService;

    @Autowired
    private MedicineReservationRepository medicineReservationRepository;

    @Autowired
    private MiniAppPhoneVerificationRepository miniAppPhoneVerificationRepository;

    @Autowired
    private SmsService smsService;

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
        Long reservationOwnerTelegramId = resolveReservationOwnerTelegramUserId(request.getTelegramUserId());

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
        medicineReservationRepository.save(reservation);
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
        Long resolvedTelegramUserId = requireTelegramUserId(telegramUserId);

        return medicineReservationRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(
                resolvedTelegramUserId,
                List.of(MedicineReservationStatus.PENDING, MedicineReservationStatus.APPROVED))
            .stream()
            .collect(Collectors.collectingAndThen(Collectors.toList(), this::toMiniAppReservationCards));
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
            .collect(Collectors.collectingAndThen(Collectors.toList(), this::toMiniAppReservationCards));
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
        if (request.getUserId() == null) {
            throw new RuntimeException("userId is required");
        }
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

    private Long resolveReservationOwnerTelegramUserId(Long telegramUserId) {
        if (telegramUserId != null) {
            return telegramUserId;
        }

        if (allowMissingTelegramUserId && devTelegramUserId > 0) {
            return devTelegramUserId;
        }

        throw new RuntimeException(
            "telegramUserId is required. For local development only, enable tenahub.mini-app.confirm.allow-missing-telegram-user-id=true and set tenahub.mini-app.confirm.dev-telegram-user-id to a valid test Telegram user id."
        );
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
        for (MedicineReservation reservation : groupedReservations) {
            reservation.setPendingExpiresAt(pendingExpiresAt);
            reservation.setQrToken(groupQrToken);
            medicineReservationRepository.save(reservation);
        }

        clearMiniAppReservationSessions(reservationOwnerTelegramId);
        notifyGroupedReservationCreatedAfterCommit(groupedReservations, pharmacy.getTelegramId());

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

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifyReservationCreated(reservation, customerPhone, customerName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyReservationCreated(reservation, customerPhone, customerName);
            }
        });
    }

    private void notifyReservationCreated(MedicineReservation reservation,
                                          String customerPhone,
                                          String customerName) {
        if (reservation == null) {
            return;
        }

        try {
            if (requiresPrescriptionUploadBeforePharmacyNotification(reservation)) {
                telegramClient.sendMessage(
                    reservation.getUserId(),
                    "🧾 Your reservation is saved, but it has not been sent to the pharmacy yet. Upload at least one prescription file in the mini app to continue."
                );
                return;
            }

            reservationWorkflowService.notifyPharmacyPendingReservation(reservation, pendingReservationTimeoutMinutes);

            telegramClient.sendMessage(
                reservation.getUserId(),
                localizationService.text(
                    reservation.getUserId(),
                    "reservation_contact_sent",
                    reservation.getMedicineName(),
                    reservation.getRequestedQuantity(),
                    customerName,
                    customerPhone
                ) + "\n⏱ Auto-cancels in " + pendingReservationTimeoutMinutes + " minutes if not approved."
            );
        } catch (Exception notificationError) {
            log.warn("Mini app reservation notification failed for reservation {}: {}",
                reservation.getId(), notificationError.getMessage());
        }
    }

    private void notifyGroupedReservationCreatedAfterCommit(List<MedicineReservation> reservations,
                                                            Long pharmacyTelegramId) {
        if (reservations == null || reservations.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifyGroupedReservationCreated(reservations, pharmacyTelegramId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyGroupedReservationCreated(reservations, pharmacyTelegramId);
            }
        });
    }

    private void notifyGroupedReservationCreated(List<MedicineReservation> reservations,
                                                 Long pharmacyTelegramId) {
        try {
            String groupId = reservations.get(0).getReservationGroupId();
            if (!requiresPrescriptionUploadBeforePharmacyNotification(reservations)
                    && pharmacyTelegramId != null && pharmacyTelegramId > 0
                    && groupId != null && !groupId.isBlank()) {
                telegramClient.sendPharmacyGroupedReservationCard(pharmacyTelegramId, groupId, reservations);
            }

            Long userId = reservations.get(0).getUserId();
            if (userId != null && groupId != null && !groupId.isBlank()) {
                telegramClient.sendMultiReserveGroupedConfirmation(userId, groupId, reservations);
            }
        } catch (Exception notificationError) {
            log.warn("Mini app grouped reservation notification failed for group {}: {}",
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
            .prescriptionReviewStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
            .prescriptionRejectionReason(reservation.getPrescriptionRejectionReason())
            .pharmacyId(reservation.getPharmacyId())
            .pharmacyName(pharmacy == null ? null : pharmacy.getName())
            .medicineId(inventory == null ? null : inventory.getId())
            .medicineName(reservation.getMedicineName())
            .quantity(reservation.getRequestedQuantity())
            .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
            .expiresAt(resolveReservationExpiresAt(reservation))
            .qrToken(reservation.getQrToken())
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
                            .prescriptionReviewStatus(resolveGroupedPrescriptionStatus(group))
                            .prescriptionRejectionReason(resolveGroupedPrescriptionRejectionReason(group))
                            .pharmacyId(first.getPharmacyId())
                            .pharmacyName(pharmacy == null ? null : pharmacy.getName())
                            .medicineName(group.size() + " medicines")
                            .quantity(totalQuantity)
                            .status(resolveGroupedStatus(group))
                            .expiresAt(resolveGroupedExpiresAt(group))
                            .qrToken(first.getQrToken())
                            .createdAt(group.stream().map(MedicineReservation::getCreatedAt).filter(value -> value != null).min(LocalDateTime::compareTo).orElse(first.getCreatedAt()))
                            .phone(first.getCustomerPhone())
                            .items(items)
                            .build();
                })
                .toList();
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
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_REJECTED)) {
            return PrescriptionReviewStatus.PRESCRIPTION_REJECTED.name();
        }
        if (requiredReservations.stream().allMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_APPROVED)) {
            return PrescriptionReviewStatus.PRESCRIPTION_APPROVED.name();
        }
        if (requiredReservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED)) {
            return PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED.name();
        }
        return PrescriptionReviewStatus.PRESCRIPTION_PENDING.name();
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
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED;
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

        Comparator<PharmacyResponseDTO> comparator = buildComparator(preferences.sortOption(), hasLocation);
        if (comparator == null) {
            return stream.toList();
        }

        return stream.sorted(comparator).toList();
    }

    private SearchPreferences resolveSearchPreferences(String sort, String filter) {
        SearchOption sortOption = normalizeSearchOption(sort);
        SearchOption filterOption = normalizeSearchOption(filter);

        boolean explicitSelection = sortOption != SearchOption.NONE || filterOption != SearchOption.NONE;
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

        return new SearchPreferences(sortOption, openNowOnly, explicitSelection);
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
