package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PurchaseOrderDTO;
import com.tenahub.bot.dto.PurchaseOrderItemDTO;
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
import com.tenahub.bot.service.PharmacyPurchaseOrderService;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PharmacyPurchaseOrderServiceImpl implements PharmacyPurchaseOrderService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacySupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final MedicineLotService medicineLotService;

    @Override
    public List<PurchaseOrderDTO> list(Long pharmacyTelegramId, String status) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        List<PurchaseOrder> orders;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            orders = purchaseOrderRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId());
        } else {
            orders = purchaseOrderRepository.findByPharmacyIdAndStatusOrderByCreatedAtDesc(
                    pharmacy.getId(), parseStatus(status));
        }
        return orders.stream().map(this::toDto).toList();
    }

    @Override
    public PurchaseOrderDTO get(Long pharmacyTelegramId, Long purchaseOrderId) {
        return toDto(requireOwned(resolvePharmacy(pharmacyTelegramId).getId(), purchaseOrderId));
    }

    @Override
    @Transactional
    public PurchaseOrderDTO create(Long pharmacyTelegramId, Map<String, Object> body) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        if (body == null) {
            throw new RuntimeException("Request body is required");
        }
        Long supplierId = toLong(body.get("supplierId"));
        if (supplierId != null) {
            PharmacySupplier supplier = supplierRepository.findByIdAndPharmacyId(supplierId, pharmacy.getId())
                    .orElseThrow(() -> new RuntimeException("Supplier does not belong to this pharmacy"));
            if ("DISABLED".equalsIgnoreCase(supplier.getStatus())) {
                throw new RuntimeException("Supplier is disabled");
            }
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        if (rawItems == null || rawItems.isEmpty()) {
            throw new RuntimeException("items array is required");
        }
        LocalDateTime now = LocalDateTime.now();
        PurchaseOrder order = purchaseOrderRepository.save(PurchaseOrder.builder()
                .pharmacyId(pharmacy.getId())
                .supplierId(supplierId)
                .status(PurchaseOrderStatus.DRAFT)
                .notes(text(body.get("notes")))
                .actorTelegramId(pharmacyTelegramId)
                .createdAt(now)
                .build());
        for (Map<String, Object> raw : rawItems) {
            String medicineName = text(raw.get("medicineName"));
            Integer quantity = toInteger(raw.get("quantity") != null ? raw.get("quantity") : raw.get("quantityOrdered"));
            if (medicineName == null || quantity == null || quantity < 1) {
                throw new RuntimeException("Each item needs medicineName and quantity >= 1");
            }
            purchaseOrderItemRepository.save(PurchaseOrderItem.builder()
                    .purchaseOrderId(order.getId())
                    .medicineName(MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName))
                    .quantityOrdered(quantity)
                    .quantityReceived(0)
                    .purchasePrice(toBigDecimal(raw.get("purchasePrice")))
                    .sellingPrice(toBigDecimal(raw.get("sellingPrice")))
                    .batchNumber(text(raw.get("batchNumber")))
                    .expiryDate(toLocalDate(raw.get("expiryDate")))
                    .build());
        }
        return toDto(order);
    }

    @Override
    @Transactional
    public PurchaseOrderDTO updateStatus(Long pharmacyTelegramId, Long purchaseOrderId, String status) {
        PurchaseOrder order = requireOwned(resolvePharmacy(pharmacyTelegramId).getId(), purchaseOrderId);
        PurchaseOrderStatus next = parseStatus(status);
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED || order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new RuntimeException("Purchase order can no longer change status");
        }
        if (next == PurchaseOrderStatus.ORDERED) {
            if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
                throw new RuntimeException("Only draft orders can be marked ordered");
            }
            order.setStatus(PurchaseOrderStatus.ORDERED);
            order.setOrderedAt(LocalDateTime.now());
        } else if (next == PurchaseOrderStatus.CANCELLED) {
            if (order.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
                throw new RuntimeException("Partially received orders cannot be cancelled");
            }
            order.setStatus(PurchaseOrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
        } else if (next == PurchaseOrderStatus.RECEIVED || next == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new RuntimeException("Use the receive endpoint to mark stock as received");
        } else if (next == PurchaseOrderStatus.DRAFT) {
            throw new RuntimeException("Cannot revert to draft");
        }
        return toDto(purchaseOrderRepository.save(order));
    }

    @Override
    @Transactional
    public PurchaseOrderDTO receive(Long pharmacyTelegramId, Long purchaseOrderId, Map<String, Object> body) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PurchaseOrder order = requireOwned(pharmacy.getId(), purchaseOrderId);
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new RuntimeException("Cancelled purchase orders cannot be received");
        }
        if (order.getStatus() == PurchaseOrderStatus.DRAFT) {
            order.setStatus(PurchaseOrderStatus.ORDERED);
            order.setOrderedAt(LocalDateTime.now());
        }
        List<PurchaseOrderItem> lines = purchaseOrderItemRepository.findByPurchaseOrderIdOrderByIdAsc(order.getId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = body == null ? null : (List<Map<String, Object>>) body.get("items");
        if (rawItems == null || rawItems.isEmpty()) {
            throw new RuntimeException("items array is required");
        }
        PharmacySupplier supplier = order.getSupplierId() == null
                ? null
                : supplierRepository.findById(order.getSupplierId()).orElse(null);
        String supplierName = supplier == null ? null : supplier.getName();
        for (Map<String, Object> raw : rawItems) {
            Long itemId = toLong(raw.get("itemId"));
            Integer incoming = toInteger(raw.get("quantity") != null ? raw.get("quantity") : raw.get("quantityReceived"));
            if (itemId == null || incoming == null || incoming < 1) {
                throw new RuntimeException("Each receive line needs itemId and quantity >= 1");
            }
            PurchaseOrderItem line = lines.stream()
                    .filter(item -> itemId.equals(item.getId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Purchase order item does not belong to this order"));
            int already = line.getQuantityReceived() == null ? 0 : line.getQuantityReceived();
            int remaining = line.getQuantityOrdered() - already;
            if (incoming > remaining) {
                throw new RuntimeException("Cannot receive more than ordered for " + line.getMedicineName());
            }
            String batchNumber = text(raw.get("batchNumber")) != null ? text(raw.get("batchNumber")) : line.getBatchNumber();
            LocalDate expiryDate = toLocalDate(raw.get("expiryDate")) != null
                    ? toLocalDate(raw.get("expiryDate"))
                    : line.getExpiryDate();
            PharmacyInventory sku = inventoryRepository
                    .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), line.getMedicineName())
                    .orElseGet(() -> PharmacyInventory.builder()
                            .pharmacyId(pharmacy.getId())
                            .medicineName(line.getMedicineName())
                            .quantity(0)
                            .outOfStock(true)
                            .price(line.getSellingPrice() != null ? line.getSellingPrice() : line.getPurchasePrice())
                            .currency("ETB")
                            .build());
            if (line.getSellingPrice() != null) {
                sku.setPrice(line.getSellingPrice());
            }
            inventoryRepository.save(sku);
            medicineLotService.receiveWithCost(
                    sku,
                    incoming,
                    batchNumber,
                    expiryDate,
                    sku.getPrice(),
                    line.getPurchasePrice(),
                    supplierName,
                    order.getSupplierId(),
                    pharmacyTelegramId,
                    StockMovementType.RECEIVED,
                    "Purchase order #" + order.getId(),
                    null);
            line.setQuantityReceived(already + incoming);
            if (batchNumber != null) {
                line.setBatchNumber(batchNumber);
            }
            if (expiryDate != null) {
                line.setExpiryDate(expiryDate);
            }
            purchaseOrderItemRepository.save(line);
        }
        boolean anyReceived = false;
        boolean allReceived = true;
        for (PurchaseOrderItem line : purchaseOrderItemRepository.findByPurchaseOrderIdOrderByIdAsc(order.getId())) {
            int received = line.getQuantityReceived() == null ? 0 : line.getQuantityReceived();
            if (received > 0) {
                anyReceived = true;
            }
            if (received < line.getQuantityOrdered()) {
                allReceived = false;
            }
        }
        if (allReceived) {
            order.setStatus(PurchaseOrderStatus.RECEIVED);
            order.setReceivedAt(LocalDateTime.now());
        } else if (anyReceived) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
        return toDto(purchaseOrderRepository.save(order));
    }

    private PurchaseOrderDTO toDto(PurchaseOrder order) {
        String supplierName = null;
        if (order.getSupplierId() != null) {
            supplierName = supplierRepository.findById(order.getSupplierId()).map(PharmacySupplier::getName).orElse(null);
        }
        List<PurchaseOrderItemDTO> items = purchaseOrderItemRepository.findByPurchaseOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(item -> PurchaseOrderItemDTO.builder()
                        .itemId(item.getId())
                        .medicineName(item.getMedicineName())
                        .quantityOrdered(item.getQuantityOrdered())
                        .quantityReceived(item.getQuantityReceived())
                        .purchasePrice(item.getPurchasePrice())
                        .sellingPrice(item.getSellingPrice())
                        .batchNumber(item.getBatchNumber())
                        .expiryDate(item.getExpiryDate())
                        .build())
                .toList();
        return PurchaseOrderDTO.builder()
                .purchaseOrderId(order.getId())
                .supplierId(order.getSupplierId())
                .supplierName(supplierName)
                .status(order.getStatus() == null ? null : order.getStatus().name())
                .notes(order.getNotes())
                .actorTelegramId(order.getActorTelegramId())
                .createdAt(order.getCreatedAt())
                .orderedAt(order.getOrderedAt())
                .receivedAt(order.getReceivedAt())
                .items(items)
                .build();
    }

    private PurchaseOrder requireOwned(Long pharmacyId, Long purchaseOrderId) {
        return purchaseOrderRepository.findByIdAndPharmacyId(purchaseOrderId, pharmacyId)
                .orElseThrow(() -> new RuntimeException("Purchase order does not belong to this pharmacy"));
    }

    private Pharmacy resolvePharmacy(Long pharmacyTelegramId) {
        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            throw new RuntimeException("pharmacyTelegramId is required");
        }
        return pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }

    private PurchaseOrderStatus parseStatus(String status) {
        try {
            return PurchaseOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (Exception e) {
            throw new RuntimeException("Unsupported purchase order status: " + status);
        }
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return LocalDate.parse(text);
    }
}
