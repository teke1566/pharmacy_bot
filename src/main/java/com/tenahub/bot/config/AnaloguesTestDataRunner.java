package com.tenahub.bot.config;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MedicineCatalogService;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(3)
@ConditionalOnProperty(name = "tenahub.seed.analogues-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AnaloguesTestDataRunner implements CommandLineRunner {

    private record SeedItem(String name, int quantity, BigDecimal price, boolean insulinFamily) {
    }

    private static final List<SeedItem> SEEDS = List.of(
            new SeedItem("insulin glargine", 6, new BigDecimal("420.00"), true),
            new SeedItem("insulin lispro", 5, new BigDecimal("380.00"), true),
            new SeedItem("paracetamol 500mg tab", 12, new BigDecimal("45.00"), false)
    );

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final MedicineCatalogService medicineCatalogService;

    @Override
    public void run(String... args) {
        Pharmacy pharmacy = resolvePharmacy();
        if (pharmacy == null) {
            log.warn("Analogue test seed skipped: no pharmacy found");
            return;
        }

        boolean insulinRx = inventoryRepository.findByMedicineNameContainingIgnoreCase("insulin").stream()
                .anyMatch(PharmacyInventory::isRequiresPrescription);

        int created = 0;
        for (SeedItem seed : SEEDS) {
            String canonical = MedicineSearchNormalizer.normalizeToEnglishCanonical(seed.name());
            if (canonical.isBlank()) {
                continue;
            }
            if (inventoryRepository.existsByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), canonical)) {
                continue;
            }

            PharmacyInventory item = PharmacyInventory.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(canonical)
                    .quantity(seed.quantity())
                    .outOfStock(false)
                    .price(seed.price())
                    .currency("ETB")
                    .requiresPrescription(seed.insulinFamily() && insulinRx)
                    .lowStockThreshold(5)
                    .lowStockAlertSent(false)
                    .updatedAt(LocalDateTime.now())
                    .build();
            medicineCatalogService.attachCatalogMedicine(item);
            inventoryRepository.save(item);
            created++;
        }

        log.info("Analogue test seed complete: pharmacyId={}, created={}", pharmacy.getId(), created);
    }

    private Pharmacy resolvePharmacy() {
        List<Pharmacy> approved = pharmacyRepository.findByApprovedTrue();
        Pharmacy preferred = approved.stream()
                .filter(pharmacy -> !pharmacy.isLicenseSuspended())
                .findFirst()
                .orElse(approved.isEmpty() ? null : approved.get(0));
        if (preferred != null) {
            return preferred;
        }
        List<Pharmacy> all = pharmacyRepository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }
}
