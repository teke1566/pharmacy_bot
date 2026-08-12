package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/inventory", "/proxyapi/api/pharmacy/inventory"})
public class PharmacyMiniAppInventoryController {

    private final InventoryService inventoryService;
    private final MiniAppActorResolver miniAppActorResolver;

    @GetMapping
    public ResponseEntity<?> getInventory(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] GET inventory, pharmacyTelegramId={}", pharmacyTelegramId);
            List<PharmacyMiniAppInventoryItemDTO> items = inventoryService.getPharmacyMiniAppInventory(pharmacyTelegramId);
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
            Integer threshold = toInteger(body.get("threshold"));
            log.info("[PharmacyMiniApp] POST inventory, medicine={}, qty={}, price={}, pharmacyTelegramId={}", medicineName, quantity, price, pharmacyTelegramId);
            PharmacyMiniAppInventoryItemDTO result = inventoryService.addStockFromMiniApp(pharmacyTelegramId, medicineName, quantity, price, threshold);
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

    @PutMapping("/low-stock-threshold")
    public ResponseEntity<?> setLowStockThreshold(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            Integer threshold = toInteger(body.get("threshold"));
            String medicineName = body.get("medicineName") != null ? body.get("medicineName").toString() : null;
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
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            inventoryService.importInventoryCsv(pharmacyTelegramId, csvContent);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message("Import complete").build());
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
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            BigDecimal price = toBigDecimal(body.get("price"));
            log.info("[PharmacyMiniApp] PUT price, itemId={}, price={}, pharmacyTelegramId={}", itemId, price, pharmacyTelegramId);
            PharmacyMiniAppInventoryItemDTO result = inventoryService.updatePriceFromMiniApp(pharmacyTelegramId, itemId, price);
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

            PharmacyMiniAppInventoryItemDTO result = null;

            if (body.containsKey("quantity") || body.containsKey("stock")) {
                Object raw = body.containsKey("quantity") ? body.get("quantity") : body.get("stock");
                result = inventoryService.updateStockFromMiniApp(pharmacyTelegramId, itemId, toInteger(raw));
            }
            if (body.containsKey("price")) {
                result = inventoryService.updatePriceFromMiniApp(pharmacyTelegramId, itemId, toBigDecimal(body.get("price")));
            }
            if (body.containsKey("prescriptionRequired") || body.containsKey("requiresPrescription")) {
                Object raw = body.containsKey("prescriptionRequired") ? body.get("prescriptionRequired") : body.get("requiresPrescription");
                result = inventoryService.togglePrescriptionFromMiniApp(pharmacyTelegramId, itemId, Boolean.TRUE.equals(raw));
            }
            if (body.containsKey("inStock") || body.containsKey("available")) {
                Object raw = body.containsKey("inStock") ? body.get("inStock") : body.get("available");
                result = inventoryService.toggleAvailabilityFromMiniApp(pharmacyTelegramId, itemId, Boolean.TRUE.equals(raw));
            }

            if (result == null) {
                return ResponseEntity.badRequest()
                        .body(MiniAppOperationResponseDTO.builder().success(false).message("No recognized fields in request body").build());
            }
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] PATCH item rejected: {}", e.getMessage());
            return error(e);
        }
    }

    private Long resolve(Long headerValue, Long paramValue) {
        return miniAppActorResolver.requirePharmacyTelegramId(headerValue, paramValue);
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }

    private ResponseEntity<?> error(RuntimeException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().contains("does not belong")
                ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }
}
