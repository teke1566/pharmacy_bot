package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PurchaseOrderDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacySupplier;
import com.tenahub.bot.entity.PurchaseOrder;
import com.tenahub.bot.entity.PurchaseOrderItem;
import com.tenahub.bot.entity.PurchaseOrderStatus;
import com.tenahub.bot.entity.StockMovementType;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacySupplierRepository;
import com.tenahub.bot.repository.PurchaseOrderItemRepository;
import com.tenahub.bot.repository.PurchaseOrderRepository;
import com.tenahub.bot.service.MedicineLotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyPurchaseOrderServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacySupplierRepository supplierRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;
    @Mock
    private MedicineLotService medicineLotService;

    private PharmacyPurchaseOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PharmacyPurchaseOrderServiceImpl(
                pharmacyRepository,
                supplierRepository,
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                inventoryRepository,
                medicineLotService);
    }

    @Test
    void get_otherPharmacy_throws() {
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(3L).telegramId(9001L).build()));
        when(purchaseOrderRepository.findByIdAndPharmacyId(44L, 3L)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.get(9001L, 44L));
        assertTrue(error.getMessage().contains("does not belong"));
    }

    @Test
    void receive_updatesLotsAndMarksReceived() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).build();
        PurchaseOrder order = PurchaseOrder.builder()
                .id(44L)
                .pharmacyId(3L)
                .supplierId(8L)
                .status(PurchaseOrderStatus.ORDERED)
                .build();
        PurchaseOrderItem line = PurchaseOrderItem.builder()
                .id(12L)
                .purchaseOrderId(44L)
                .medicineName("insulin")
                .quantityOrdered(10)
                .quantityReceived(0)
                .purchasePrice(new BigDecimal("8.00"))
                .sellingPrice(new BigDecimal("12.00"))
                .build();
        PharmacyInventory sku = PharmacyInventory.builder()
                .id(22L)
                .pharmacyId(3L)
                .medicineName("insulin")
                .quantity(1)
                .price(new BigDecimal("12.00"))
                .build();

        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(purchaseOrderRepository.findByIdAndPharmacyId(44L, 3L)).thenReturn(Optional.of(order));
        when(purchaseOrderItemRepository.findByPurchaseOrderIdOrderByIdAsc(44L)).thenReturn(List.of(line));
        when(supplierRepository.findById(8L)).thenReturn(Optional.of(
                PharmacySupplier.builder().id(8L).pharmacyId(3L).name("MediSupply").build()));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(3L, "insulin")).thenReturn(Optional.of(sku));
        when(inventoryRepository.save(any(PharmacyInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderItemRepository.save(any(PurchaseOrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of(
                "itemId", 12L,
                "quantity", 10,
                "batchNumber", "LOT-1",
                "expiryDate", LocalDate.now().plusMonths(8).toString())));

        PurchaseOrderDTO dto = service.receive(9001L, 44L, body);

        assertEquals("RECEIVED", dto.getStatus());
        assertEquals(10, dto.getItems().get(0).getQuantityReceived());
        verify(medicineLotService).receiveWithCost(
                eq(sku),
                eq(10),
                eq("LOT-1"),
                any(LocalDate.class),
                eq(sku.getPrice()),
                eq(line.getPurchasePrice()),
                eq("MediSupply"),
                eq(8L),
                eq(9001L),
                eq(StockMovementType.RECEIVED),
                eq("Purchase order #44"),
                org.mockito.ArgumentMatchers.isNull());
    }
}
