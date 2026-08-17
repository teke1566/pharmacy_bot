package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacySupplierDTO;
import com.tenahub.bot.entity.MedicineBatch;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacySupplier;
import com.tenahub.bot.repository.MedicineBatchRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacySupplierRepository;
import com.tenahub.bot.service.PharmacySupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PharmacySupplierServiceImpl implements PharmacySupplierService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacySupplierRepository supplierRepository;
    private final MedicineBatchRepository batchRepository;

    @Override
    public List<PharmacySupplierDTO> list(Long pharmacyTelegramId, String search) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        String needle = search == null ? "" : search.trim();
        List<PharmacySupplier> suppliers = needle.isBlank()
                ? supplierRepository.findByPharmacyIdOrderByNameAsc(pharmacy.getId())
                : supplierRepository.findByPharmacyIdAndNameContainingIgnoreCaseOrderByNameAsc(pharmacy.getId(), needle);
        return suppliers.stream().map(this::toDto).toList();
    }

    @Override
    public PharmacySupplierDTO get(Long pharmacyTelegramId, Long supplierId) {
        return toDto(requireOwned(resolvePharmacy(pharmacyTelegramId).getId(), supplierId));
    }

    @Override
    @Transactional
    public PharmacySupplierDTO create(Long pharmacyTelegramId, Map<String, Object> body) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        String name = text(body, "name");
        if (name == null) {
            throw new RuntimeException("name is required");
        }
        LocalDateTime now = LocalDateTime.now();
        PharmacySupplier supplier = supplierRepository.save(PharmacySupplier.builder()
                .pharmacyId(pharmacy.getId())
                .name(name)
                .phone(text(body, "phone"))
                .email(text(body, "email"))
                .address(text(body, "address"))
                .contactPerson(text(body, "contactPerson"))
                .status("ACTIVE")
                .notes(text(body, "notes"))
                .createdAt(now)
                .updatedAt(now)
                .build());
        return toDto(supplier);
    }

    @Override
    @Transactional
    public PharmacySupplierDTO update(Long pharmacyTelegramId, Long supplierId, Map<String, Object> body) {
        PharmacySupplier supplier = requireOwned(resolvePharmacy(pharmacyTelegramId).getId(), supplierId);
        if (body.containsKey("name")) {
            String name = text(body, "name");
            if (name == null) {
                throw new RuntimeException("name is required");
            }
            supplier.setName(name);
        }
        if (body.containsKey("phone")) {
            supplier.setPhone(text(body, "phone"));
        }
        if (body.containsKey("email")) {
            supplier.setEmail(text(body, "email"));
        }
        if (body.containsKey("address")) {
            supplier.setAddress(text(body, "address"));
        }
        if (body.containsKey("contactPerson")) {
            supplier.setContactPerson(text(body, "contactPerson"));
        }
        if (body.containsKey("notes")) {
            supplier.setNotes(text(body, "notes"));
        }
        if (body.containsKey("status")) {
            String status = text(body, "status");
            supplier.setStatus(status == null ? supplier.getStatus() : status.toUpperCase(Locale.ROOT));
        }
        supplier.setUpdatedAt(LocalDateTime.now());
        return toDto(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public PharmacySupplierDTO disable(Long pharmacyTelegramId, Long supplierId) {
        PharmacySupplier supplier = requireOwned(resolvePharmacy(pharmacyTelegramId).getId(), supplierId);
        supplier.setStatus("DISABLED");
        supplier.setUpdatedAt(LocalDateTime.now());
        return toDto(supplierRepository.save(supplier));
    }

    private PharmacySupplierDTO toDto(PharmacySupplier supplier) {
        List<String> medicines = batchRepository.findByPharmacyId(supplier.getPharmacyId()).stream()
                .filter(lot -> Objects.equals(supplier.getId(), lot.getSupplierId())
                        || (lot.getSupplier() != null && lot.getSupplier().equalsIgnoreCase(supplier.getName())))
                .map(MedicineBatch::getMedicineName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return PharmacySupplierDTO.builder()
                .supplierId(supplier.getId())
                .name(supplier.getName())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .address(supplier.getAddress())
                .contactPerson(supplier.getContactPerson())
                .status(supplier.getStatus())
                .notes(supplier.getNotes())
                .createdAt(supplier.getCreatedAt())
                .suppliedMedicines(medicines)
                .build();
    }

    private PharmacySupplier requireOwned(Long pharmacyId, Long supplierId) {
        return supplierRepository.findByIdAndPharmacyId(supplierId, pharmacyId)
                .orElseThrow(() -> new RuntimeException("Supplier does not belong to this pharmacy"));
    }

    private Pharmacy resolvePharmacy(Long pharmacyTelegramId) {
        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            throw new RuntimeException("pharmacyTelegramId is required");
        }
        return pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }

    private String text(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        String value = body.get(key).toString().trim();
        return value.isEmpty() ? null : value;
    }
}
