package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.dto.PriceChangeSubmitRequestDTO;
import com.tenahub.bot.dto.PharmacyPricingItemDTO;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.entity.PharmacyStaffRole;
import com.tenahub.bot.entity.PriceHistory;
import com.tenahub.bot.repository.MedicineBatchRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyPricingPolicyRepository;
import com.tenahub.bot.repository.PriceChangeRequestRepository;
import com.tenahub.bot.repository.PriceHistoryRepository;
import com.tenahub.bot.repository.PromotionRepository;
import com.tenahub.bot.service.PharmacyAuditService;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacyNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyPricingServiceImplTest {

    @Mock private PharmacyInventoryRepository inventoryRepository;
    @Mock private MedicineBatchRepository medicineBatchRepository;
    @Mock private PharmacyPricingPolicyRepository policyRepository;
    @Mock private PriceHistoryRepository priceHistoryRepository;
    @Mock private PriceChangeRequestRepository priceChangeRequestRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PharmacyAuthorizationService authorizationService;
    @Mock private PharmacyAuditService pharmacyAuditService;
    @Mock private PharmacyNotificationService pharmacyNotificationService;

    @InjectMocks
    private PharmacyPricingServiceImpl service;

    private PharmacyActor owner() {
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
    void submitChange_publishesAndWritesHistory() {
        PharmacyInventory item = PharmacyInventory.builder()
                .id(10L)
                .pharmacyId(3L)
                .medicineName("Amoxicillin")
                .price(new BigDecimal("600.00"))
                .purchaseCost(new BigDecimal("450.00"))
                .currency("ETB")
                .version(1L)
                .build();
        when(inventoryRepository.findById(10L)).thenReturn(Optional.of(item));
        when(policyRepository.findByPharmacyId(3L)).thenReturn(Optional.empty());
        when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(promotionRepository.findByPharmacyIdAndInventoryIdAndStatus(eq(3L), eq(10L), any()))
                .thenReturn(List.of());
        when(medicineBatchRepository.findByInventoryId(10L)).thenReturn(List.of());

        Object result = service.submitChange(owner(), 10L, PriceChangeSubmitRequestDTO.builder()
                .proposedSellingPrice(new BigDecimal("650.00"))
                .reason("Supplier cost increase")
                .expectedVersion(1L)
                .build());

        PharmacyPricingItemDTO dto = (PharmacyPricingItemDTO) result;
        assertEquals(new BigDecimal("650.00"), dto.getSellingPrice());
        ArgumentCaptor<PriceHistory> historyCaptor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryRepository).save(historyCaptor.capture());
        assertEquals(new BigDecimal("600.00"), historyCaptor.getValue().getOldSellingPrice());
        assertEquals(new BigDecimal("650.00"), historyCaptor.getValue().getNewSellingPrice());
    }

    @Test
    void submitChange_rejectsNegativePrice() {
        PharmacyInventory item = PharmacyInventory.builder()
                .id(10L).pharmacyId(3L).medicineName("X").price(new BigDecimal("10")).version(0L).build();
        when(inventoryRepository.findById(10L)).thenReturn(Optional.of(item));

        assertThrows(RuntimeException.class, () -> service.submitChange(owner(), 10L,
                PriceChangeSubmitRequestDTO.builder()
                        .proposedSellingPrice(new BigDecimal("-1"))
                        .reason("bad")
                        .build()));
    }

    @Test
    void submitChange_versionConflict() {
        PharmacyInventory item = PharmacyInventory.builder()
                .id(10L).pharmacyId(3L).medicineName("X").price(new BigDecimal("10")).version(5L).build();
        when(inventoryRepository.findById(10L)).thenReturn(Optional.of(item));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.submitChange(owner(), 10L,
                PriceChangeSubmitRequestDTO.builder()
                        .proposedSellingPrice(new BigDecimal("12"))
                        .reason("bump")
                        .expectedVersion(4L)
                        .build()));
        assertEquals("Price was changed by another user. Refresh and review the latest price.", error.getMessage());
    }
}
