package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pharmacyId;

    private Long userId;

    private String medicineName;

    private Integer requestedQuantity;

    @Enumerated(EnumType.STRING)
    private MedicineReservationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime fulfilledAt;

    @Builder.Default
    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent = false;

    private String rejectionReason;

    private String customerPhone;

    private String customerName;

    private String note;

    @Column(name = "first_reminder_sent_at")
    private LocalDateTime firstReminderSentAt;

    @Column(name = "second_reminder_sent_at")
    private LocalDateTime secondReminderSentAt;

    @Column(name = "sla_escalated_at")
    private LocalDateTime slaEscalatedAt;

    @Column(name = "pending_expires_at")
    private LocalDateTime pendingExpiresAt;

    @Column(name = "qr_token")
    private String qrToken;

    @Builder.Default
    @Column(name = "inventory_held", nullable = false)
    private boolean inventoryHeld = false;

    @Builder.Default
    @Column(name = "prescription_required", nullable = false)
    private boolean prescriptionRequired = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "prescription_review_status", nullable = false)
    private PrescriptionReviewStatus prescriptionReviewStatus = PrescriptionReviewStatus.NOT_REQUIRED;

    @Column(name = "prescription_reviewed_at")
    private LocalDateTime prescriptionReviewedAt;

    @Column(name = "prescription_reviewed_by")
    private Long prescriptionReviewedBy;

    @Column(name = "prescription_rejection_reason")
    private String prescriptionRejectionReason;

    @Column(name = "prescription_clarification_message", length = 2000)
    private String prescriptionClarificationMessage;

    @Column(name = "prescription_clarification_at")
    private LocalDateTime prescriptionClarificationAt;

    @Column(name = "fulfilled_by_telegram_id")
    private Long fulfilledByTelegramId;

    @Column(name = "reservation_group_id")
    private String reservationGroupId;

    @Column(name = "hidden_from_user_at")
    private LocalDateTime hiddenFromUserAt;

    @Column(name = "hidden_from_pharmacy_at")
    private LocalDateTime hiddenFromPharmacyAt;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private java.math.BigDecimal unitPrice;

    @Column(name = "total_price", precision = 12, scale = 2)
    private java.math.BigDecimal totalPrice;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "price_locked_at")
    private LocalDateTime priceLockedAt;
}