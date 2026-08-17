package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacySupplierDTO;

import java.util.List;
import java.util.Map;

public interface PharmacySupplierService {

    List<PharmacySupplierDTO> list(Long pharmacyTelegramId, String search);

    PharmacySupplierDTO get(Long pharmacyTelegramId, Long supplierId);

    PharmacySupplierDTO create(Long pharmacyTelegramId, Map<String, Object> body);

    PharmacySupplierDTO update(Long pharmacyTelegramId, Long supplierId, Map<String, Object> body);

    PharmacySupplierDTO disable(Long pharmacyTelegramId, Long supplierId);
}
