package com.tenahub.bot.config;

import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.MedicineRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.service.MedicineCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class MedicineCatalogBackfillRunner implements CommandLineRunner {

    private final PharmacyInventoryRepository inventoryRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineCatalogService medicineCatalogService;

    @Override
    public void run(String... args) {
        List<PharmacyInventory> items = inventoryRepository.findAll();
        if (items.isEmpty()) {
            return;
        }

        int linked = 0;
        Set<Long> catalogIds = new HashSet<>();
        for (PharmacyInventory item : items) {
            if (item.getMedicineName() == null || item.getMedicineName().isBlank()) {
                continue;
            }
            try {
                if (item.getCatalogMedicineId() == null) {
                    medicineCatalogService.attachCatalogMedicine(item);
                    inventoryRepository.save(item);
                    linked++;
                }
                if (item.getCatalogMedicineId() != null) {
                    catalogIds.add(item.getCatalogMedicineId());
                }
            } catch (RuntimeException e) {
                log.warn("Catalog backfill skipped for inventory id={}: {}", item.getId(), e.getMessage());
            }
        }

        for (Long catalogId : catalogIds) {
            medicineCatalogService.refreshPrescriptionRequired(catalogId);
        }

        medicineCatalogService.repairDerivedFields();

        log.info("Medicine catalog backfill complete: linked={}, catalogRows={}", linked, medicineRepository.count());
    }
}
