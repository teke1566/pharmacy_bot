package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineAvailabilityAlert;
import com.tenahub.bot.entity.UserLocation;
import com.tenahub.bot.repository.MedicineAvailabilityAlertRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.UserFavoritePharmacyRepository;
import com.tenahub.bot.service.RatingService;
import com.tenahub.bot.util.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class MedicineAvailabilityAlertServiceImplTest {

    @Mock
    private MedicineAvailabilityAlertRepository alertRepository;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;
    @Mock
    private RatingService ratingService;
    @Mock
    private UserFavoritePharmacyRepository favoritePharmacyRepository;

    private MedicineAvailabilityAlertServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MedicineAvailabilityAlertServiceImpl(
                alertRepository, telegramClient, inventoryRepository, ratingService, favoritePharmacyRepository);
        ReflectionTestUtils.setField(service, "defaultRadiusKm", 25.0);
        ReflectionTestUtils.setField(service, "defaultCooldownMinutes", 180);
        ReflectionTestUtils.setField(service, "defaultMaxNotifications", 5);
        ReflectionTestUtils.setField(service, "defaultExpiryDays", 30);
        ReflectionTestUtils.setField(service, "maxActiveAlertsPerUser", 20);
    }

    @Test
    void createAlert_savesNormalizedActiveAlert() {
        when(alertRepository.countByUserIdAndActiveTrue(8L)).thenReturn(0L);
        when(alertRepository.findByUserIdAndMedicineNameIgnoreCaseAndActiveTrue(8L, "paracetamol"))
                .thenReturn(Optional.empty());
        when(alertRepository.findTopByUserIdAndMedicineNameIgnoreCaseOrderByIdDesc(8L, "paracetamol"))
                .thenReturn(Optional.empty());

        service.createAlert(8L, "Panadol", UserLocation.builder().latitude(9.01).longitude(38.75).build());

        ArgumentCaptor<MedicineAvailabilityAlert> captor = ArgumentCaptor.forClass(MedicineAvailabilityAlert.class);
        verify(alertRepository).save(captor.capture());
        assertEquals("paracetamol", captor.getValue().getMedicineName());
        assertTrue(captor.getValue().isActive());
        assertEquals(25.0, captor.getValue().getRadiusKm());
        assertEquals(9.01, captor.getValue().getLatitude());
    }

    @Test
    void createAlert_throwsWhenActiveLimitReached() {
        when(alertRepository.countByUserIdAndActiveTrue(8L)).thenReturn(20L);
        when(alertRepository.findByUserIdAndMedicineNameIgnoreCaseAndActiveTrue(8L, "paracetamol"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.createAlert(8L, "paracetamol", null));
    }

    @Test
    void removeAlert_rejectsOtherUsersAlert() {
        when(alertRepository.findById(4L)).thenReturn(Optional.of(
                MedicineAvailabilityAlert.builder().id(4L).userId(99L).active(true).build()));

        assertThrows(RuntimeException.class, () -> service.removeAlert(8L, 4L));
    }

    @Test
    void removeAlert_deactivatesOwnedAlert() {
        MedicineAvailabilityAlert alert = MedicineAvailabilityAlert.builder()
                .id(4L)
                .userId(8L)
                .active(true)
                .build();
        when(alertRepository.findById(4L)).thenReturn(Optional.of(alert));

        service.removeAlert(8L, 4L);

        ArgumentCaptor<MedicineAvailabilityAlert> captor = ArgumentCaptor.forClass(MedicineAvailabilityAlert.class);
        verify(alertRepository).save(captor.capture());
        assertFalse(captor.getValue().isActive());
    }

    @Test
    void removeAllAlerts_deactivatesActiveAlerts() {
        MedicineAvailabilityAlert first = MedicineAvailabilityAlert.builder().id(1L).userId(8L).active(true).build();
        when(alertRepository.findByUserIdAndActiveTrue(8L)).thenReturn(List.of(first));

        service.removeAllAlerts(8L);

        verify(alertRepository).saveAll(any());
        assertFalse(first.isActive());
    }
}
