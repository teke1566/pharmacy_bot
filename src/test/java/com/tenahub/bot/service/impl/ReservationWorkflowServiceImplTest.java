package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.util.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationWorkflowServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private TelegramClient telegramClient;

    private ReservationWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReservationWorkflowServiceImpl(pharmacyRepository, telegramClient);
    }

    @Test
    void notifyPharmacyPendingReservation_sendsToPharmacyTelegramId() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(10L)
                .pharmacyId(5L)
                .userId(9L)
                .medicineName("paracetamol")
                .requestedQuantity(3)
                .customerPhone("+251911000000")
                .customerName("Abel")
                .prescriptionRequired(false)
                .prescriptionReviewStatus(PrescriptionReviewStatus.NOT_REQUIRED)
                .build();
        Pharmacy pharmacy = Pharmacy.builder().id(5L).telegramId(777L).build();
        when(pharmacyRepository.findById(5L)).thenReturn(Optional.of(pharmacy));

        service.notifyPharmacyPendingReservation(reservation, 20L);

        verify(telegramClient).sendReservationRequestToPharmacy(
                777L, 10L, 9L, "paracetamol", 3, "+251911000000", "Abel", 20L, false);
    }

    @Test
    void notifyPharmacyPendingReservation_rxAwaitingUpload_stillSends() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(11L)
                .pharmacyId(7L)
                .userId(12L)
                .medicineName("amoxicillin")
                .requestedQuantity(2)
                .customerPhone("+251900000000")
                .customerName("Liya")
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.UPLOAD_REQUIRED)
                .build();
        Pharmacy pharmacy = Pharmacy.builder().id(7L).telegramId(888L).build();
        when(pharmacyRepository.findById(7L)).thenReturn(Optional.of(pharmacy));

        service.notifyPharmacyPendingReservation(reservation, 20L);

        verify(telegramClient).sendReservationRequestToPharmacy(
                888L, 11L, 12L, "amoxicillin", 2, "+251900000000", "Liya", 20L, true);
    }

    @Test
    void notifyPharmacyPendingReservation_missingTelegramId_doesNotSend() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(12L)
                .pharmacyId(8L)
                .build();
        Pharmacy pharmacy = Pharmacy.builder().id(8L).telegramId(null).build();
        when(pharmacyRepository.findById(8L)).thenReturn(Optional.of(pharmacy));

        service.notifyPharmacyPendingReservation(reservation, 20L);

        verify(telegramClient, never()).sendReservationRequestToPharmacy(
                anyLong(), any(), any(), anyString(), any(), anyString(), anyString(), anyLong(), anyBoolean());
    }

    @Test
    void notifyPharmacyPendingReservations_groupedCardUsesPharmacyTelegramId() {
        MedicineReservation first = MedicineReservation.builder()
                .id(21L)
                .pharmacyId(9L)
                .reservationGroupId("group-abc")
                .medicineName("paracetamol")
                .requestedQuantity(1)
                .build();
        MedicineReservation second = MedicineReservation.builder()
                .id(22L)
                .pharmacyId(9L)
                .reservationGroupId("group-abc")
                .medicineName("ibuprofen")
                .requestedQuantity(2)
                .build();
        Pharmacy pharmacy = Pharmacy.builder().id(9L).telegramId(999L).build();
        when(pharmacyRepository.findById(9L)).thenReturn(Optional.of(pharmacy));

        service.notifyPharmacyPendingReservations(List.of(first, second), 20L);

        verify(telegramClient).sendPharmacyGroupedReservationCard(999L, "group-abc", List.of(first, second));
    }
}
