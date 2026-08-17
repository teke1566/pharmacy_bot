package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.dto.PharmacySaleDTO;
import com.tenahub.bot.dto.PharmacySalesSummaryDTO;
import com.tenahub.bot.dto.PharmacySupplierDTO;
import com.tenahub.bot.dto.PurchaseOrderDTO;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.entity.PharmacyStaffRole;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacyPurchaseOrderService;
import com.tenahub.bot.service.PharmacySalesService;
import com.tenahub.bot.service.PharmacySupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyMiniAppOpsControllersTest {

    @Mock
    private MiniAppActorResolver miniAppActorResolver;
    @Mock
    private PharmacySalesService pharmacySalesService;
    @Mock
    private PharmacySupplierService pharmacySupplierService;
    @Mock
    private PharmacyPurchaseOrderService pharmacyPurchaseOrderService;
    @Mock
    private PharmacyAuthorizationService pharmacyAuthorizationService;

    private PharmacyActor ownerActor() {
        return PharmacyActor.builder()
                .pharmacyId(3L)
                .pharmacyTelegramId(9001L)
                .actorTelegramId(9001L)
                .role(PharmacyStaffRole.PHARMACY_OWNER)
                .permissions(EnumSet.allOf(PharmacyPermission.class))
                .build();
    }

    @BeforeEach
    void setUp() {
        when(miniAppActorResolver.requirePharmacyActor(9001L, null)).thenReturn(ownerActor());
        doNothing().when(pharmacyAuthorizationService).require(any(), any());
    }

    @Test
    void salesSummary_usesResolver() {
        when(pharmacySalesService.summary(9001L, "daily"))
                .thenReturn(PharmacySalesSummaryDTO.builder().period("daily").revenue(BigDecimal.ZERO).saleCount(0).build());

        PharmacyMiniAppSalesController controller =
                new PharmacyMiniAppSalesController(pharmacySalesService, miniAppActorResolver, pharmacyAuthorizationService);
        ResponseEntity<?> response = controller.summary(9001L, null, "daily");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pharmacySalesService).summary(9001L, "daily");
        verify(pharmacyAuthorizationService).require(any(), eq(PharmacyPermission.SALES_VIEW));
    }

    @Test
    void salesHistory_usesResolver() {
        when(pharmacySalesService.history(9001L, "weekly")).thenReturn(List.of(PharmacySaleDTO.builder().saleId(1L).build()));

        PharmacyMiniAppSalesController controller =
                new PharmacyMiniAppSalesController(pharmacySalesService, miniAppActorResolver, pharmacyAuthorizationService);
        ResponseEntity<?> response = controller.history(9001L, null, "weekly");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pharmacySalesService).history(9001L, "weekly");
    }

    @Test
    void supplierGet_otherPharmacy_returnsForbidden() {
        when(pharmacySupplierService.get(9001L, 8L))
                .thenThrow(new RuntimeException("Supplier does not belong to this pharmacy"));

        PharmacyMiniAppSuppliersController controller =
                new PharmacyMiniAppSuppliersController(pharmacySupplierService, miniAppActorResolver, pharmacyAuthorizationService);
        ResponseEntity<?> response = controller.get(8L, 9001L, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        MiniAppOperationResponseDTO body = (MiniAppOperationResponseDTO) response.getBody();
        assertEquals("Supplier does not belong to this pharmacy", body.getMessage());
    }

    @Test
    void purchaseOrderGet_otherPharmacy_returnsForbidden() {
        when(pharmacyPurchaseOrderService.get(9001L, 44L))
                .thenThrow(new RuntimeException("Purchase order does not belong to this pharmacy"));

        PharmacyMiniAppPurchaseOrdersController controller =
                new PharmacyMiniAppPurchaseOrdersController(pharmacyPurchaseOrderService, miniAppActorResolver, pharmacyAuthorizationService);
        ResponseEntity<?> response = controller.get(44L, 9001L, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void supplierCreate_usesService() {
        when(pharmacySupplierService.create(9001L, Map.of("name", "MediSupply")))
                .thenReturn(PharmacySupplierDTO.builder().supplierId(8L).name("MediSupply").build());

        PharmacyMiniAppSuppliersController controller =
                new PharmacyMiniAppSuppliersController(pharmacySupplierService, miniAppActorResolver, pharmacyAuthorizationService);
        ResponseEntity<?> response = controller.create(9001L, null, Map.of("name", "MediSupply"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(pharmacyAuthorizationService).require(any(), eq(PharmacyPermission.SUPPLIER_CREATE));
    }

    @Test
    void purchaseOrderReceive_usesService() {
        Map<String, Object> body = Map.of("items", List.of(Map.of("itemId", 12L, "quantity", 2)));
        when(pharmacyPurchaseOrderService.receive(9001L, 44L, body))
                .thenReturn(PurchaseOrderDTO.builder().purchaseOrderId(44L).status("RECEIVED").build());

        PharmacyMiniAppPurchaseOrdersController controller =
                new PharmacyMiniAppPurchaseOrdersController(pharmacyPurchaseOrderService, miniAppActorResolver, pharmacyAuthorizationService);
        ResponseEntity<?> response = controller.receive(44L, 9001L, null, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pharmacyPurchaseOrderService).receive(9001L, 44L, body);
        verify(pharmacyAuthorizationService).require(any(), eq(PharmacyPermission.PURCHASE_ORDER_RECEIVE));
    }
}
