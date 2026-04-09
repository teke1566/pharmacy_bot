package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PrescriptionFileMetadataDTO;
import com.tenahub.bot.dto.PrescriptionReviewRequestDTO;
import com.tenahub.bot.dto.PrescriptionStatusItemDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.entity.ReservationPrescriptionFile;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.ReservationPrescriptionFileRepository;
import com.tenahub.bot.service.PrescriptionReviewService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionReviewServiceImpl implements PrescriptionReviewService {

    private final MedicineReservationRepository reservationRepository;
    private final ReservationPrescriptionFileRepository prescriptionFileRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository pharmacyInventoryRepository;
    private final ReservationService reservationService;
    private final TelegramClient telegramClient;

    @Value("${tenahub.reservation.pending-timeout-minutes:20}")
    private long pendingReservationTimeoutMinutes;

    @Override
    @Transactional
    public PrescriptionStatusResponseDTO uploadPrescriptionFiles(Long reservationId,
                                                                 String reservationGroupId,
                                                                 Long userId,
                                                                 Long pharmacyId,
                                                                 Long medicineId,
                                                                 List<MultipartFile> files) {
        List<MedicineReservation> reservations = resolveReservations(reservationId, reservationGroupId);
        List<MedicineReservation> requiredReservations = filterPrescriptionReservations(reservations, medicineId);

        if (requiredReservations.isEmpty()) {
            throw new RuntimeException("No prescription-required reservation found for upload");
        }
        if (files == null || files.stream().noneMatch(file -> file != null && !file.isEmpty())) {
            throw new RuntimeException("At least one prescription file is required");
        }

        validateReservationStateForUpload(requiredReservations);

        Long resolvedUserId = requiredReservations.get(0).getUserId();
        Long resolvedPharmacyId = requiredReservations.get(0).getPharmacyId();
        if (userId != null && !Objects.equals(userId, resolvedUserId)) {
            throw new RuntimeException("userId does not match reservation owner");
        }
        if (pharmacyId != null && !Objects.equals(pharmacyId, resolvedPharmacyId)) {
            throw new RuntimeException("pharmacyId does not match reservation");
        }

        Long resolvedMedicineId = resolveMedicineId(medicineId, requiredReservations);
        String resolvedGroupId = resolveGroupId(requiredReservations);
        LocalDateTime uploadedAt = LocalDateTime.now();
        boolean hadPrescriptionFilesBeforeUpload = !collectPrescriptionFiles(reservations).isEmpty();
        int savedFileCount = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            try {
                prescriptionFileRepository.save(ReservationPrescriptionFile.builder()
                        .reservationId(requiredReservations.size() == 1 ? requiredReservations.get(0).getId() : null)
                        .reservationGroupId(resolvedGroupId)
                        .userId(resolvedUserId)
                        .pharmacyId(resolvedPharmacyId)
                        .medicineId(resolvedMedicineId)
                        .originalFilename(file.getOriginalFilename() == null ? "prescription" : file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .fileSize(file.getSize())
                        .fileData(file.getBytes())
                        .uploadedAt(uploadedAt)
                        .reviewStatus(PrescriptionReviewStatus.PRESCRIPTION_PENDING)
                        .build());
                savedFileCount++;
            } catch (IOException e) {
                throw new RuntimeException("Failed to store prescription file", e);
            }
        }

        if (savedFileCount <= 0) {
            throw new RuntimeException("At least one prescription file is required");
        }

        LocalDateTime pendingExpiresAt = uploadedAt.plusMinutes(pendingReservationTimeoutMinutes);
        for (MedicineReservation reservation : reservations) {
            if (reservation.isPrescriptionRequired()) {
                reservation.setPrescriptionRequired(true);
                reservation.setPrescriptionReviewStatus(PrescriptionReviewStatus.PRESCRIPTION_PENDING);
                reservation.setPrescriptionReviewedAt(null);
                reservation.setPrescriptionReviewedBy(null);
                reservation.setPrescriptionRejectionReason(null);
            }
            reservation.setPendingExpiresAt(pendingExpiresAt);
            reservation.setFirstReminderSentAt(null);
            reservation.setSecondReminderSentAt(null);
            reservation.setSlaEscalatedAt(null);
            reservationRepository.save(reservation);
        }

        PrescriptionStatusResponseDTO statusResponse = buildStatusResponse(reservations);
    boolean shouldNotifyPharmacy = !hadPrescriptionFilesBeforeUpload
        && statusResponse.getFiles() != null
        && !statusResponse.getFiles().isEmpty();
    if (shouldNotifyPharmacy) {
            notifyPharmacyPrescriptionReadyAfterCommit(statusResponse);
        }

        return statusResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionStatusResponseDTO getPrescriptionStatus(Long reservationId,
                                                               String reservationGroupId,
                                                               Long userId) {
        List<MedicineReservation> reservations = resolveReservations(reservationId, reservationGroupId);
        Long resolvedUserId = reservations.get(0).getUserId();
        if (userId != null && !Objects.equals(userId, resolvedUserId)) {
            throw new RuntimeException("userId does not match reservation owner");
        }

        return buildStatusResponse(reservations);
    }

    @Override
    @Transactional
    public PrescriptionStatusResponseDTO reviewPrescription(Long reservationId,
                                                            String reservationGroupId,
                                                            Long pharmacyTelegramId,
                                                            PrescriptionReviewRequestDTO request) {
        List<MedicineReservation> reservations = resolveReservations(reservationId, reservationGroupId);
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (!Objects.equals(pharmacy.getId(), reservations.get(0).getPharmacyId())) {
            throw new RuntimeException("Reservation does not belong to this pharmacy");
        }

        PrescriptionReviewStatus decision = normalizeDecision(request == null ? null : request.getDecision());
        String rejectionReason = request == null ? null : request.getRejectionReason();
        List<MedicineReservation> requiredReservations = reservations.stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .toList();

        if (requiredReservations.isEmpty()) {
            throw new RuntimeException("This reservation does not require prescription review");
        }

        LocalDateTime reviewedAt = LocalDateTime.now();
        List<ReservationPrescriptionFile> files = collectPrescriptionFiles(reservations);

        if (decision == PrescriptionReviewStatus.PRESCRIPTION_APPROVED) {
            if (files.isEmpty()) {
                throw new RuntimeException("No prescription files have been uploaded for this reservation");
            }

            for (MedicineReservation reservation : requiredReservations) {
                if (reservation.getStatus() != com.tenahub.bot.entity.MedicineReservationStatus.PENDING) {
                    throw new RuntimeException("Only pending reservations can have prescription approved");
                }
                reservation.setPrescriptionReviewStatus(PrescriptionReviewStatus.PRESCRIPTION_APPROVED);
                reservation.setPrescriptionReviewedAt(reviewedAt);
                reservation.setPrescriptionReviewedBy(pharmacyTelegramId);
                reservation.setPrescriptionRejectionReason(null);
                reservationRepository.save(reservation);
            }

            for (ReservationPrescriptionFile file : files) {
                file.setReviewStatus(PrescriptionReviewStatus.PRESCRIPTION_APPROVED);
                file.setReviewedAt(reviewedAt);
                file.setReviewedBy(pharmacyTelegramId);
                file.setRejectionReason(null);
                prescriptionFileRepository.save(file);
            }
        } else {
            String resolvedReason = rejectionReason == null || rejectionReason.isBlank()
                    ? "Prescription rejected by pharmacy"
                    : rejectionReason.trim();

            for (MedicineReservation reservation : requiredReservations) {
                MedicineReservation rejectedReservation = reservation;
                if (reservation.getStatus() == com.tenahub.bot.entity.MedicineReservationStatus.PENDING) {
                    rejectedReservation = reservationService.rejectReservation(reservation.getId(), resolvedReason);
                }
                rejectedReservation.setPrescriptionReviewStatus(PrescriptionReviewStatus.PRESCRIPTION_REJECTED);
                rejectedReservation.setPrescriptionReviewedAt(reviewedAt);
                rejectedReservation.setPrescriptionReviewedBy(pharmacyTelegramId);
                rejectedReservation.setPrescriptionRejectionReason(resolvedReason);
                reservationRepository.save(rejectedReservation);
            }

            for (ReservationPrescriptionFile file : files) {
                file.setReviewStatus(PrescriptionReviewStatus.PRESCRIPTION_REJECTED);
                file.setReviewedAt(reviewedAt);
                file.setReviewedBy(pharmacyTelegramId);
                file.setRejectionReason(resolvedReason);
                prescriptionFileRepository.save(file);
            }
        }

        return buildStatusResponse(reservations);
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionFileContent downloadPrescriptionFile(Long prescriptionId, Long pharmacyTelegramId) {
        ReservationPrescriptionFile file = prescriptionFileRepository.findById(prescriptionId)
                .orElseThrow(() -> new RuntimeException("Prescription file not found"));
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (!Objects.equals(pharmacy.getId(), file.getPharmacyId())) {
            throw new RuntimeException("Prescription does not belong to this pharmacy");
        }

        return new PrescriptionFileContent(file.getFileData(), file.getContentType(), file.getOriginalFilename());
    }

    private List<MedicineReservation> resolveReservations(Long reservationId, String reservationGroupId) {
        if (reservationId == null && (reservationGroupId == null || reservationGroupId.isBlank())) {
            throw new RuntimeException("reservationId or reservationGroupId is required");
        }

        if (reservationId != null) {
            MedicineReservation reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new RuntimeException("Reservation not found"));
            return List.of(reservation);
        }

        List<MedicineReservation> reservations = reservationRepository.findByReservationGroupIdOrderByCreatedAtDesc(reservationGroupId.trim());
        if (reservations.isEmpty()) {
            throw new RuntimeException("Reservation group not found");
        }
        return reservations;
    }

    private List<MedicineReservation> filterPrescriptionReservations(List<MedicineReservation> reservations, Long medicineId) {
        return reservations.stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .filter(reservation -> medicineId == null || Objects.equals(resolveReservationMedicineId(reservation), medicineId))
                .toList();
    }

    private void validateReservationStateForUpload(List<MedicineReservation> reservations) {
        for (MedicineReservation reservation : reservations) {
            switch (reservation.getStatus()) {
                case PENDING -> {
                }
                case APPROVED, FULFILLED, CANCELLED, EXPIRED, REJECTED ->
                        throw new RuntimeException("Prescription files can only be uploaded for pending reservations");
            }
        }
    }

    private Long resolveMedicineId(Long requestedMedicineId, List<MedicineReservation> reservations) {
        if (requestedMedicineId != null) {
            PharmacyInventory inventory = pharmacyInventoryRepository.findById(requestedMedicineId)
                    .orElseThrow(() -> new RuntimeException("Medicine not found"));
            if (!Objects.equals(inventory.getPharmacyId(), reservations.get(0).getPharmacyId())) {
                throw new RuntimeException("Medicine does not belong to this pharmacy");
            }
            return requestedMedicineId;
        }

        if (reservations.size() == 1) {
            return resolveReservationMedicineId(reservations.get(0));
        }

        return null;
    }

    private Long resolveReservationMedicineId(MedicineReservation reservation) {
        if (reservation == null) {
            return null;
        }

        return pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(
                        reservation.getPharmacyId(),
                        reservation.getMedicineName())
                .map(PharmacyInventory::getId)
                .orElse(null);
    }

    private String resolveGroupId(List<MedicineReservation> reservations) {
        return reservations.stream()
                .map(MedicineReservation::getReservationGroupId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private PrescriptionReviewStatus normalizeDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new RuntimeException("decision is required");
        }

        String normalized = decision.trim().toLowerCase();
        return switch (normalized) {
            case "approve", "approved", "prescription_approved" -> PrescriptionReviewStatus.PRESCRIPTION_APPROVED;
            case "reject", "rejected", "prescription_rejected" -> PrescriptionReviewStatus.PRESCRIPTION_REJECTED;
            default -> throw new RuntimeException("decision must be approve or reject");
        };
    }

    private List<ReservationPrescriptionFile> collectPrescriptionFiles(List<MedicineReservation> reservations) {
        List<Long> reservationIds = reservations.stream()
                .map(MedicineReservation::getId)
                .toList();
        Map<Long, ReservationPrescriptionFile> files = new LinkedHashMap<>();

        prescriptionFileRepository.findByReservationIdInOrderByUploadedAtAsc(reservationIds)
                .forEach(file -> files.put(file.getId(), file));

        String reservationGroupId = resolveGroupId(reservations);
        if (reservationGroupId != null) {
            prescriptionFileRepository.findByReservationGroupIdOrderByUploadedAtAsc(reservationGroupId)
                    .forEach(file -> files.put(file.getId(), file));
        }

        return new ArrayList<>(files.values());
    }

    private PrescriptionStatusResponseDTO buildStatusResponse(List<MedicineReservation> reservations) {
        List<ReservationPrescriptionFile> files = collectPrescriptionFiles(reservations);
        List<MedicineReservation> sortedReservations = reservations.stream()
                .sorted(Comparator.comparing(MedicineReservation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        MedicineReservation first = sortedReservations.get(0);
        PrescriptionReviewStatus reviewStatus = aggregateReviewStatus(sortedReservations);
        LocalDateTime reviewedAt = sortedReservations.stream()
                .map(MedicineReservation::getPrescriptionReviewedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        Long reviewedBy = sortedReservations.stream()
                .map(MedicineReservation::getPrescriptionReviewedBy)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String rejectionReason = sortedReservations.stream()
                .map(MedicineReservation::getPrescriptionRejectionReason)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);

        List<PrescriptionStatusItemDTO> items = sortedReservations.stream()
                .map(reservation -> PrescriptionStatusItemDTO.builder()
                        .reservationId(reservation.getId())
                        .reservationGroupId(reservation.getReservationGroupId())
                        .pharmacyId(reservation.getPharmacyId())
                        .medicineId(resolveReservationMedicineId(reservation))
                        .medicineName(reservation.getMedicineName())
                        .prescriptionRequired(reservation.isPrescriptionRequired())
                        .reviewStatus(reservation.getPrescriptionReviewStatus().name())
                        .reviewedAt(reservation.getPrescriptionReviewedAt())
                        .reviewedBy(reservation.getPrescriptionReviewedBy())
                        .rejectionReason(reservation.getPrescriptionRejectionReason())
                        .build())
                .toList();

        List<PrescriptionFileMetadataDTO> fileDTOs = files.stream()
                .map(file -> PrescriptionFileMetadataDTO.builder()
                        .prescriptionId(file.getId())
                        .reservationId(file.getReservationId())
                        .reservationGroupId(file.getReservationGroupId())
                        .userId(file.getUserId())
                        .pharmacyId(file.getPharmacyId())
                        .medicineId(file.getMedicineId())
                        .originalFilename(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .fileSize(file.getFileSize())
                        .uploadedAt(file.getUploadedAt())
                        .reviewStatus(file.getReviewStatus().name())
                        .reviewedAt(file.getReviewedAt())
                        .reviewedBy(file.getReviewedBy())
                        .rejectionReason(file.getRejectionReason())
                        .build())
                .toList();

        return PrescriptionStatusResponseDTO.builder()
                .reservationId(sortedReservations.size() == 1 ? first.getId() : null)
                .reservationGroupId(resolveGroupId(sortedReservations))
                .userId(first.getUserId())
                .pharmacyId(first.getPharmacyId())
                .prescriptionRequired(sortedReservations.stream().anyMatch(MedicineReservation::isPrescriptionRequired))
                .reviewStatus(reviewStatus.name())
                .reviewedAt(reviewedAt)
                .reviewedBy(reviewedBy)
                .rejectionReason(rejectionReason)
                .items(items)
                .files(fileDTOs)
                .build();
    }

    private PrescriptionReviewStatus aggregateReviewStatus(List<MedicineReservation> reservations) {
        List<MedicineReservation> requiredReservations = reservations.stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .toList();

        if (requiredReservations.isEmpty()) {
            return PrescriptionReviewStatus.NOT_REQUIRED;
        }
        if (requiredReservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_REJECTED)) {
            return PrescriptionReviewStatus.PRESCRIPTION_REJECTED;
        }
        if (requiredReservations.stream().allMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_APPROVED)) {
            return PrescriptionReviewStatus.PRESCRIPTION_APPROVED;
        }
        if (requiredReservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED)) {
            return PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED;
        }
        return PrescriptionReviewStatus.PRESCRIPTION_PENDING;
    }

    private void notifyPharmacyPrescriptionReadyAfterCommit(PrescriptionStatusResponseDTO statusResponse) {
        if (statusResponse == null || statusResponse.getPharmacyId() == null) {
            return;
        }

        Runnable notifier = () -> {
            try {
                Long reservationId = statusResponse.getReservationId();
                int fileCount = statusResponse.getFiles() == null ? 0 : statusResponse.getFiles().size();
                Long pharmacyTelegramId = pharmacyRepository.findById(statusResponse.getPharmacyId())
                        .map(Pharmacy::getTelegramId)
                        .orElse(null);
                System.out.println("[PRESC_NOTIFY] reservationId=" + reservationId + ", fileCount=" + fileCount + ", pharmacyTelegramId=" + pharmacyTelegramId);
                if (pharmacyTelegramId != null && pharmacyTelegramId > 0 && fileCount > 0) {
                    System.out.println("[PRESC_NOTIFY] Calling sendPharmacyPrescriptionReviewCard");
                    telegramClient.sendPharmacyPrescriptionReviewCard(pharmacyTelegramId, statusResponse);
                } else {
                    System.out.println("[PRESC_NOTIFY] Notification skipped: invalid pharmacyTelegramId or fileCount=0");
                }
            } catch (Exception e) {
                System.out.println("[PRESC_NOTIFY] Exception: " + e.getMessage());
                e.printStackTrace();
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
}