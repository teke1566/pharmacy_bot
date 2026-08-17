package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyMiniAppAddStockRequest;
import com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO;
import com.tenahub.bot.dto.PharmacyMiniAppInventoryPatchRequest;
import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.dto.PriceChangeSubmitRequestDTO;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacyPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/inventory", "/proxyapi/api/pharmacy/inventory"})
public class PharmacyMiniAppInventoryController {

    private final InventoryService inventoryService;
    private final MiniAppActorResolver miniAppActorResolver;
    private final PharmacyPricingService pharmacyPricingService;
    private final PharmacyAuthorizationService pharmacyAuthorizationService;

    @GetMapping
    public ResponseEntity<?> getInventory(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "stockStatus", required = false) String stockStatus,
            @RequestParam(value = "expiryStatus", required = false) String expiryStatus,
            @RequestParam(value = "includeArchived", required = false) Boolean includeArchived) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] GET inventory, pharmacyTelegramId={}, search={}, stockStatus={}, expiryStatus={}",
                    pharmacyTelegramId, search, stockStatus, expiryStatus);
            List<PharmacyMiniAppInventoryItemDTO> items = inventoryService.getPharmacyMiniAppInventory(
                    pharmacyTelegramId, search, stockStatus, expiryStatus, includeArchived);
            return ResponseEntity.ok(items);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] GET inventory rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        }
    }

    @PostMapping
    public ResponseEntity<?> addStock(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String medicineName = body.get("medicineName") != null ? body.get("medicineName").toString() : null;
            Integer quantity = toInteger(body.get("quantity"));
            BigDecimal price = toBigDecimal(body.get("price"));
            Integer threshold = toInteger(body.get("threshold") != null ? body.get("threshold") : body.get("lowStockThreshold"));
            Boolean requiresPrescription = toBoolean(body.containsKey("requiresPrescription")
                    ? body.get("requiresPrescription")
                    : body.get("prescriptionRequired"));
            String batchNumber = toText(body.get("batchNumber"));
            LocalDate expiryDate = toLocalDate(body.get("expiryDate"));
            String strength = toText(body.get("strength"));
            String dosageForm = toText(body.get("dosageForm"));
            String manufacturer = toText(body.get("manufacturer"));
            log.info("[PharmacyMiniApp] POST inventory, medicine={}, qty={}, price={}, pharmacyTelegramId={}", medicineName, quantity, price, pharmacyTelegramId);
            PharmacyMiniAppInventoryItemDTO result = inventoryService.addStockFromMiniApp(
                    pharmacyTelegramId,
                    PharmacyMiniAppAddStockRequest.builder()
                            .medicineName(medicineName)
                            .quantity(quantity)
                            .price(price)
                            .lowStockThreshold(threshold)
                            .requiresPrescription(requiresPrescription)
                            .batchNumber(batchNumber)
                            .expiryDate(expiryDate)
                            .strength(strength)
                            .dosageForm(dosageForm)
                            .manufacturer(manufacturer)
                            .build());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] POST inventory rejected: {}", e.getMessage());
            return error(e);
        }
    }

    // --- Named sub-resource endpoints (must be before /{itemId} to avoid path conflicts) ---

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "period", required = false, defaultValue = "all") String period) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String summary = inventoryService.buildSummary(pharmacyTelegramId, period);
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/low-stock")
    public ResponseEntity<?> getLowStock(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String alert = inventoryService.buildLowStockAlert(pharmacyTelegramId);
            return ResponseEntity.ok(Map.of("lowStockAlert", alert));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/expiry")
    public ResponseEntity<?> getExpiryAlert(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "bucket", required = false) String bucket) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            if (bucket != null && !bucket.isBlank()) {
                return ResponseEntity.ok(inventoryService.listExpiryBatches(pharmacyTelegramId, bucket));
            }
            String alert = inventoryService.buildExpiryAlert(pharmacyTelegramId);
            return ResponseEntity.ok(Map.of("expiryAlert", alert));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/low-stock-threshold")
    public ResponseEntity<?> setLowStockThreshold(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            Integer threshold = toInteger(body.get("threshold"));
            String medicineName = body.get("medicineName") != null ? body.get("medicineName").toString() : null;
            if (medicineName == null || medicineName.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(MiniAppOperationResponseDTO.builder().success(false).message("medicineName is required").build());
            }
            inventoryService.setLowStockThreshold(pharmacyTelegramId, medicineName, threshold);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Threshold updated").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/demand-insights")
    public ResponseEntity<?> getDemandInsights(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String insights = inventoryService.getDemandInsights(pharmacyTelegramId);
            return ResponseEntity.ok(Map.of("demandInsights", insights));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/restock-suggestions")
    public ResponseEntity<?> getRestockSuggestions(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String suggestions = inventoryService.getAdvancedRestockSuggestions(pharmacyTelegramId);
            return ResponseEntity.ok(Map.of("restockSuggestions", suggestions));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/restock-items")
    public ResponseEntity<?> getRestockItems(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(inventoryService.listRestockSuggestions(pharmacyTelegramId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/restock-items/ignore")
    public ResponseEntity<?> ignoreRestockItem(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            String medicineName = body != null && body.get("medicineName") != null
                    ? body.get("medicineName").toString()
                    : null;
            inventoryService.ignoreRestockSuggestion(pharmacyTelegramId, medicineName);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder()
                    .success(true)
                    .message("Restock suggestion ignored")
                    .build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportCsv(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            byte[] csv = inventoryService.exportInventoryCsv(pharmacyTelegramId);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=inventory.csv")
                    .body(csv);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<?> importCsv(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody String csvContent) {
        try {
            PharmacyActor actor = miniAppActorResolver.requirePharmacyActor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.INVENTORY_EDIT);
            var result = inventoryService.importInventoryCsv(actor.getPharmacyTelegramId(), csvContent);
            if (result != null && !result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> bulkUpdate(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items == null || items.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(MiniAppOperationResponseDTO.builder().success(false).message("items array is required").build());
            }
            StringBuilder text = new StringBuilder();
            for (Map<String, Object> item : items) {
                String name = item.get("medicineName") != null ? item.get("medicineName").toString() : "";
                Object qty = item.get("quantity");
                Object price = item.get("price");
                text.append(name).append(" | ").append(qty != null ? qty : "").append(" | ").append(price != null ? price : "").append("\n");
            }
            InventoryService.BulkInventoryUpdateResult result = inventoryService.bulkUpsertFromText(pharmacyTelegramId, text.toString());
            return ResponseEntity.ok(Map.of("success", true, "totalLines", result.totalLines(),
                    "updatedCount", result.updatedCount(), "failedCount", result.failedCount(), "errors", result.errors()));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    // --- Item-specific endpoints (/{itemId} path variable) ---

    @GetMapping("/{itemId}/batches")
    public ResponseEntity<?> listBatches(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(inventoryService.listInventoryBatches(pharmacyTelegramId, itemId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/{itemId}/movements")
    public ResponseEntity<?> listMovements(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            return ResponseEntity.ok(inventoryService.listInventoryMovements(pharmacyTelegramId, itemId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{itemId}/adjust")
    public ResponseEntity<?> adjustItem(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            Integer quantityChange = toInteger(body.get("quantityChange"));
            String reason = toText(body.get("reason"));
            String type = toText(body.get("type") != null ? body.get("type") : body.get("movementType"));
            String batchNumber = toText(body.get("batchNumber"));
            LocalDate expiryDate = toLocalDate(body.get("expiryDate"));
            PharmacyMiniAppInventoryItemDTO result = inventoryService.adjustInventoryFromMiniApp(
                    pharmacyTelegramId, itemId, quantityChange, reason, type, batchNumber, expiryDate);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/{itemId}/stock")
    public ResponseEntity<?> updateStock(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            Integer quantity = toInteger(body.get("quantity"));
            log.info("[PharmacyMiniApp] PUT stock, itemId={}, qty={}, pharmacyTelegramId={}", itemId, quantity, pharmacyTelegramId);
            PharmacyMiniAppInventoryItemDTO result = inventoryService.updateStockFromMiniApp(pharmacyTelegramId, itemId, quantity);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] PUT stock rejected: {}", e.getMessage());
            return error(e);
        }
    }

    @PutMapping("/{itemId}/price")
    public ResponseEntity<?> updatePrice(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            PharmacyActor actor = miniAppActorResolver.requirePharmacyActor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, PharmacyPermission.PRICE_EDIT);
            BigDecimal price = toBigDecimal(body.get("price"));
            String reason = body.get("reason") == null ? "Inventory price update" : String.valueOf(body.get("reason"));
            log.info("[PharmacyMiniApp] PUT price via pricing service, itemId={}, price={}, pharmacyTelegramId={}",
                    itemId, price, actor.getPharmacyTelegramId());
            Object result = pharmacyPricingService.submitChange(actor, itemId, PriceChangeSubmitRequestDTO.builder()
                    .proposedSellingPrice(price)
                    .reason(reason)
                    .expectedVersion(body.get("version") == null ? null : Long.valueOf(String.valueOf(body.get("version"))))
                    .forceBelowCost(Boolean.TRUE.equals(body.get("forceBelowCost")))
                    .build());
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] PUT price rejected: {}", e.getMessage());
            return error(e);
        }
    }

    @PutMapping("/{itemId}/prescription")
    public ResponseEntity<?> togglePrescription(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Boolean> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            boolean required = Boolean.TRUE.equals(body.get("requiresPrescription"));
            log.info("[PharmacyMiniApp] PUT prescription, itemId={}, required={}, pharmacyTelegramId={}", itemId, required, pharmacyTelegramId);
            PharmacyMiniAppInventoryItemDTO result = inventoryService.togglePrescriptionFromMiniApp(pharmacyTelegramId, itemId, required);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] PUT prescription rejected: {}", e.getMessage());
            return error(e);
        }
    }

    @PutMapping("/{itemId}/availability")
    public ResponseEntity<?> toggleAvailability(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Boolean> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            boolean available = Boolean.TRUE.equals(body.get("available"));
            log.info("[PharmacyMiniApp] PUT availability, itemId={}, available={}, pharmacyTelegramId={}", itemId, available, pharmacyTelegramId);
            PharmacyMiniAppInventoryItemDTO result = inventoryService.toggleAvailabilityFromMiniApp(pharmacyTelegramId, itemId, available);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] PUT availability rejected: {}", e.getMessage());
            return error(e);
        }
    }

    @PatchMapping("/{itemId}")
    public ResponseEntity<?> patchItem(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] PATCH item, itemId={}, fields={}, pharmacyTelegramId={}", itemId, body.keySet(), pharmacyTelegramId);

            PharmacyMiniAppInventoryPatchRequest request = PharmacyMiniAppInventoryPatchRequest.builder()
                    .quantity(body.containsKey("quantity") || body.containsKey("stock")
                            ? toInteger(body.containsKey("quantity") ? body.get("quantity") : body.get("stock"))
                            : null)
                    .price(body.containsKey("price") ? toBigDecimal(body.get("price")) : null)
                    .requiresPrescription(body.containsKey("prescriptionRequired") || body.containsKey("requiresPrescription")
                            ? toBoolean(body.containsKey("prescriptionRequired")
                            ? body.get("prescriptionRequired")
                            : body.get("requiresPrescription"))
                            : null)
                    .available(body.containsKey("inStock") || body.containsKey("available")
                            ? toBoolean(body.containsKey("inStock") ? body.get("inStock") : body.get("available"))
                            : null)
                    .lowStockThreshold(body.containsKey("lowStockThreshold") || body.containsKey("threshold")
                            ? toInteger(body.containsKey("lowStockThreshold") ? body.get("lowStockThreshold") : body.get("threshold"))
                            : null)
                    .batchNumber(body.containsKey("batchNumber") ? toText(body.get("batchNumber")) : null)
                    .expiryDate(body.containsKey("expiryDate") && body.get("expiryDate") != null && !body.get("expiryDate").toString().isBlank()
                            ? toLocalDate(body.get("expiryDate"))
                            : null)
                    .clearExpiry(body.containsKey("clearExpiry") && Boolean.TRUE.equals(toBoolean(body.get("clearExpiry")))
                            || (body.containsKey("expiryDate") && (body.get("expiryDate") == null || body.get("expiryDate").toString().isBlank())))
                    .strength(body.containsKey("strength") ? toText(body.get("strength")) : null)
                    .dosageForm(body.containsKey("dosageForm") ? toText(body.get("dosageForm")) : null)
                    .archived(body.containsKey("archived") ? toBoolean(body.get("archived")) : null)
                    .reason(body.containsKey("reason") ? toText(body.get("reason")) : null)
                    .build();

            boolean hasField = body.containsKey("quantity") || body.containsKey("stock") || body.containsKey("price")
                    || body.containsKey("prescriptionRequired") || body.containsKey("requiresPrescription")
                    || body.containsKey("inStock") || body.containsKey("available")
                    || body.containsKey("lowStockThreshold") || body.containsKey("threshold")
                    || body.containsKey("batchNumber") || body.containsKey("expiryDate") || body.containsKey("clearExpiry")
                    || body.containsKey("strength") || body.containsKey("dosageForm") || body.containsKey("archived");
            if (!hasField) {
                return ResponseEntity.badRequest()
                        .body(MiniAppOperationResponseDTO.builder().success(false).message("No recognized fields in request body").build());
            }
            PharmacyMiniAppInventoryItemDTO result = inventoryService.patchInventoryFromMiniApp(pharmacyTelegramId, itemId, request);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] PATCH item rejected: {}", e.getMessage());
            return error(e);
        }
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<?> archiveItem(
            @PathVariable Long itemId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] DELETE/archive item, itemId={}, pharmacyTelegramId={}", itemId, pharmacyTelegramId);
            inventoryService.archiveInventoryItem(pharmacyTelegramId, itemId);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("SKU archived").build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] DELETE item rejected: {}", e.getMessage());
            return error(e);
        }
    }

    private Long resolve(Long headerValue, Long paramValue) {
        return miniAppActorResolver.requirePharmacyTelegramId(headerValue, paramValue);
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        return Integer.parseInt(text);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        return new BigDecimal(text);
    }

    private Boolean toBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        return Boolean.parseBoolean(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private String toText(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        return LocalDate.parse(text);
    }

    private ResponseEntity<?> error(RuntimeException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().contains("does not belong")
                ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }
}
