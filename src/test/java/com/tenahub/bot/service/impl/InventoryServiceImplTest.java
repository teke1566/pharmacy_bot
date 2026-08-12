package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO;
import com.tenahub.bot.entity.InventoryHistory;
import com.tenahub.bot.entity.InventoryEventType;
import com.tenahub.bot.entity.LowStockThreshold;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.InventoryHistoryRepository;
import com.tenahub.bot.repository.LowStockThresholdRepository;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.MedicineSearchLogRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MedicineAvailabilityAlertService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.util.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;
    @Mock
    private InventoryHistoryRepository historyRepository;
    @Mock
    private LowStockThresholdRepository thresholdRepository;
    @Mock
    private MedicineSearchLogRepository searchLogRepository;
    @Mock
    private MedicineReservationRepository reservationRepository;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private MedicineAvailabilityAlertService medicineAvailabilityAlertService;
    @Mock
    private PharmacyService pharmacyService;

    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(
                pharmacyRepository,
                inventoryRepository,
                historyRepository,
                thresholdRepository,
                searchLogRepository,
                reservationRepository,
                telegramClient,
                medicineAvailabilityAlertService,
                pharmacyService);
    }

    @Test
    void updateStockFromMiniApp_updatesQuantityAndRecordsHistory() {
        Pharmacy pharmacy = Pharmacy.builder().id(1L).telegramId(11L).build();
        PharmacyInventory item = PharmacyInventory.builder()
                .id(100L)
                .pharmacyId(1L)
                .medicineName("paracetamol")
                .quantity(10)
                .outOfStock(false)
                .build();

        when(pharmacyRepository.findByTelegramId(11L)).thenReturn(Optional.of(pharmacy));
        when(inventoryRepository.findById(100L)).thenReturn(Optional.of(item));
        when(inventoryRepository.save(any(PharmacyInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PharmacyMiniAppInventoryItemDTO dto = service.updateStockFromMiniApp(11L, 100L, 4);

        assertEquals(4, dto.getStockQuantity());
        assertTrue(dto.isInStock());

        verify(historyRepository).save(any(InventoryHistory.class));
        ArgumentCaptor<PharmacyInventory> captor = ArgumentCaptor.forClass(PharmacyInventory.class);
        verify(inventoryRepository).save(captor.capture());
        assertEquals(4, captor.getValue().getQuantity());
    }

    @Test
    void addStockFromMiniApp_newItem_setsDefaultsAndCreatesHistory() {
        Pharmacy pharmacy = Pharmacy.builder().id(9L).telegramId(99L).build();
        when(pharmacyRepository.findByTelegramId(99L)).thenReturn(Optional.of(pharmacy));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(9L, "ibuprofen"))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(PharmacyInventory.class))).thenAnswer(invocation -> {
            PharmacyInventory saved = invocation.getArgument(0);
            saved.setId(500L);
            return saved;
        });

        PharmacyMiniAppInventoryItemDTO dto = service.addStockFromMiniApp(99L, "ibuprofen", 7, new BigDecimal("12.50"), 3);

        assertEquals(500L, dto.getItemId());
        assertEquals("ETB", dto.getCurrency());
        assertEquals(7, dto.getStockQuantity());
        assertEquals(3, dto.getLowStockThreshold());
        verify(historyRepository).save(any(InventoryHistory.class));
    }

    @Test
    void setLowStockThreshold_sendsAlertWhenCurrentStockAtOrBelowThreshold() {
        Pharmacy pharmacy = Pharmacy.builder().id(2L).telegramId(200L).build();
        PharmacyInventory item = PharmacyInventory.builder()
                .id(71L)
                .pharmacyId(2L)
                .medicineName("amoxicillin")
                .quantity(2)
                .outOfStock(false)
                .lowStockAlertSent(false)
                .build();

        when(pharmacyRepository.findByTelegramId(200L)).thenReturn(Optional.of(pharmacy));
        when(thresholdRepository.findByPharmacyIdAndMedicineNameIgnoreCase(2L, "amoxicillin"))
                .thenReturn(Optional.of(LowStockThreshold.builder().pharmacyId(2L).medicineName("amoxicillin").threshold(10).build()));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(2L, "amoxicillin"))
                .thenReturn(Optional.of(item));

        service.setLowStockThreshold(200L, "amoxicillin", 4);

        verify(telegramClient).sendLowStockAlert(200L, "amoxicillin", 2, 4);
        verify(inventoryRepository).save(item);
    }

    @Test
    void bulkUpsertFromText_reportsErrorsAndUpdatesValidLines() {
        when(pharmacyService.medicineExistsInCatalog("paracetamol")).thenReturn(true);
        when(pharmacyService.medicineExistsInCatalog("unknownmed")).thenReturn(false);

        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(300L).build();
        when(pharmacyRepository.findByTelegramId(300L)).thenReturn(Optional.of(pharmacy));

        PharmacyInventory existing = PharmacyInventory.builder()
                .id(111L)
                .pharmacyId(3L)
                .medicineName("paracetamol")
                .quantity(2)
                .outOfStock(false)
                .lowStockAlertSent(false)
                .build();
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(3L, "paracetamol"))
                .thenReturn(Optional.of(existing));
        when(thresholdRepository.findByPharmacyIdAndMedicineNameIgnoreCase(3L, "paracetamol"))
                .thenReturn(Optional.empty());

        String input = String.join("\n",
                "paracetamol | 5 | 9.99 | 3",
                "unknownmed | 1 | 1.00",
                "badline without separators");

        var result = service.bulkUpsertFromText(300L, input);

        assertEquals(3, result.totalLines());
        assertEquals(1, result.updatedCount());
        assertEquals(2, result.failedCount());
        assertEquals(2, result.errors().size());

        verify(inventoryRepository, atLeastOnce()).save(any(PharmacyInventory.class));
        verify(historyRepository).save(any(InventoryHistory.class));
    }

    @Test
    void toggleAvailabilityFromMiniApp_unavailable_marksOutOfStockAndHistory() {
        Pharmacy pharmacy = Pharmacy.builder().id(5L).telegramId(500L).build();
        PharmacyInventory item = PharmacyInventory.builder()
                .id(510L)
                .pharmacyId(5L)
                .medicineName("cetirizine")
                .quantity(4)
                .outOfStock(false)
                .build();

        when(pharmacyRepository.findByTelegramId(500L)).thenReturn(Optional.of(pharmacy));
        when(inventoryRepository.findById(510L)).thenReturn(Optional.of(item));
        when(inventoryRepository.save(any(PharmacyInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PharmacyMiniAppInventoryItemDTO dto = service.toggleAvailabilityFromMiniApp(500L, 510L, false);

        assertTrue(dto.isOutOfStock());
        assertEquals(0, dto.getStockQuantity());

        ArgumentCaptor<InventoryHistory> historyCaptor = ArgumentCaptor.forClass(InventoryHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals(InventoryEventType.MARKED_OUT, historyCaptor.getValue().getEventType());
    }
}
