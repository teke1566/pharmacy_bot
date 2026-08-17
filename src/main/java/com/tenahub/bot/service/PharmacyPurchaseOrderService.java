package com.tenahub.bot.service;

import com.tenahub.bot.dto.PurchaseOrderDTO;

import java.util.List;
import java.util.Map;

public interface PharmacyPurchaseOrderService {

    List<PurchaseOrderDTO> list(Long pharmacyTelegramId, String status);

    PurchaseOrderDTO get(Long pharmacyTelegramId, Long purchaseOrderId);

    PurchaseOrderDTO create(Long pharmacyTelegramId, Map<String, Object> body);

    PurchaseOrderDTO updateStatus(Long pharmacyTelegramId, Long purchaseOrderId, String status);

    PurchaseOrderDTO receive(Long pharmacyTelegramId, Long purchaseOrderId, Map<String, Object> body);
}
