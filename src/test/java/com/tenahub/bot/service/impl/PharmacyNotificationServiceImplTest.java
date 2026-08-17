package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyNotificationDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyNotification;
import com.tenahub.bot.entity.PharmacyNotificationType;
import com.tenahub.bot.repository.PharmacyNotificationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyNotificationServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyNotificationRepository notificationRepository;

    private PharmacyNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PharmacyNotificationServiceImpl(pharmacyRepository, notificationRepository);
    }

    @Test
    void create_persistsInboxRow() {
        service.create(5L, PharmacyNotificationType.RESERVATION_PENDING, "New reservation",
                "Reservation #10 for paracetamol", 10L, "paracetamol");

        ArgumentCaptor<PharmacyNotification> captor = ArgumentCaptor.forClass(PharmacyNotification.class);
        verify(notificationRepository).save(captor.capture());
        PharmacyNotification saved = captor.getValue();
        assertEquals(5L, saved.getPharmacyId());
        assertEquals(PharmacyNotificationType.RESERVATION_PENDING, saved.getType());
        assertEquals(10L, saved.getReservationId());
        assertEquals("paracetamol", saved.getMedicineName());
    }

    @Test
    void list_scopesToPharmacyTelegramId() {
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(5L).telegramId(9001L).build()));
        when(notificationRepository.findByPharmacyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(
                PharmacyNotification.builder()
                        .id(1L)
                        .pharmacyId(5L)
                        .type(PharmacyNotificationType.LOW_STOCK)
                        .title("Low stock")
                        .message("Batch alert")
                        .createdAt(LocalDateTime.now())
                        .build()));

        List<PharmacyNotificationDTO> rows = service.list(9001L, false);

        assertEquals(1, rows.size());
        assertEquals(1L, rows.get(0).getNotificationId());
        assertEquals("LOW_STOCK", rows.get(0).getType());
        verify(notificationRepository).findByPharmacyIdOrderByCreatedAtDesc(5L);
    }

    @Test
    void markRead_otherPharmacyNotification_throwsDoesNotBelong() {
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(5L).telegramId(9001L).build()));
        when(notificationRepository.findByIdAndPharmacyId(99L, 5L)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.markRead(9001L, 99L));
        assertTrue(error.getMessage().contains("does not belong"));
    }

    @Test
    void markRead_setsReadAt() {
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(5L).telegramId(9001L).build()));
        PharmacyNotification notification = PharmacyNotification.builder()
                .id(3L)
                .pharmacyId(5L)
                .type(PharmacyNotificationType.EXPIRY)
                .title("Expiry")
                .message("Soon")
                .createdAt(LocalDateTime.now())
                .build();
        when(notificationRepository.findByIdAndPharmacyId(3L, 5L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(PharmacyNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PharmacyNotificationDTO dto = service.markRead(9001L, 3L);

        assertTrue(dto.isRead());
        assertTrue(dto.getReadAt() != null);
    }
}
